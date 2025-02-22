package com.litongjava.translator;

import com.litongjava.translator.consts.AudioConst;
import com.litongjava.translator.utils.AudioUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class MainUI extends Application {

  private Label statusLabel = new Label("状态: 准备中");
  private static TextArea translatedText = new TextArea();
  private Canvas waveformCanvas;
  private GraphicsContext gc;
  private AudioRecorder audioRecorder;

  @Override
  public void start(Stage primaryStage) {
    primaryStage.setTitle("实时语音翻译");

    GridPane grid = new GridPane();
    grid.setPadding(new Insets(10));
    grid.setHgap(10);
    grid.setVgap(10);

    // 翻译结果区域
    grid.add(new Label("翻译结果:"), 0, 0);
    translatedText.setEditable(false);
    translatedText.setWrapText(true);
    grid.add(translatedText, 1, 0);

    // 静音阈值设置
    Slider thresholdSlider = new Slider(-60, 0, AudioConst.DEFAULT_SILENCE_THRESHOLD);
    thresholdSlider.setBlockIncrement(1);
    thresholdSlider.setMajorTickUnit(5);
    thresholdSlider.setMinorTickCount(4);
    thresholdSlider.setSnapToTicks(true);
    thresholdSlider.setShowTickLabels(true);
    thresholdSlider.setShowTickMarks(true);
    grid.add(new Label("静音阈值:"), 0, 1);
    grid.add(thresholdSlider, 1, 1);

    // 状态显示
    grid.add(statusLabel, 1, 2);

    // 波形显示
    grid.add(new Label("波形显示:"), 0, 3);
    waveformCanvas = new Canvas(800, 150);
    gc = waveformCanvas.getGraphicsContext2D();
    grid.add(waveformCanvas, 1, 3);

    // 三个控制按钮：开始、暂停、停止
    Button startBtn = new Button("开始");
    Button pauseBtn = new Button("暂停");
    Button stopBtn = new Button("停止");
    HBox buttonBox = new HBox(10, startBtn, pauseBtn, stopBtn);
    grid.add(buttonBox, 1, 4);

    // 模型选择下拉框
    ComboBox<String> modelComboBox = new ComboBox<>();
    modelComboBox.getItems().addAll("whisper-large-v3", "whisper-large-v3-turbo", "distil-whisper-large-v3-en");
    modelComboBox.setValue("whisper-large-v3-turbo");
    grid.add(new Label("选择模型:"), 0, 5);
    grid.add(modelComboBox, 1, 5);

    modelComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
      AudioProcessor.selectedModel = newVal;
    });

    Scene scene = new Scene(grid, 850, 500);
    primaryStage.setScene(scene);
    primaryStage.show();

    // 窗口关闭时退出程序
    primaryStage.setOnCloseRequest(e -> {
      if (audioRecorder != null && audioRecorder.isRunning()) {
        audioRecorder.stopRecording();
      }
      Platform.exit();
      System.exit(0);
    });

    // 初始化录音器，并传入 UI 更新回调
    audioRecorder = new AudioRecorder(this::handleAudioData);
    // 设置初始静音阈值
    audioRecorder.setSilenceThreshold(thresholdSlider.getValue());
    thresholdSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
      audioRecorder.setSilenceThreshold(newVal.doubleValue());
    });

    // 按钮事件处理
    startBtn.setOnAction(e -> {
      if (audioRecorder.isRunning() && audioRecorder.isPaused()) {
        audioRecorder.resumeRecording();
        statusLabel.setText("状态: 录音中");
      } else if (!audioRecorder.isRunning()) {
        audioRecorder.startRecording();
        statusLabel.setText("状态: 录音中");
      }
    });

    pauseBtn.setOnAction(e -> {
      if (audioRecorder.isRunning() && !audioRecorder.isPaused()) {
        audioRecorder.pauseRecording();
        statusLabel.setText("状态: 暂停");
      }
    });

    stopBtn.setOnAction(e -> {
      if (audioRecorder.isRunning()) {
        audioRecorder.stopRecording();
        statusLabel.setText("状态: 停止");
      }
    });
  }

  /**
   * 供 GroqSpeechClient 回调更新翻译文本（在 UI 线程中执行）
   */
  public static void updateUI(String translated) {
    Platform.runLater(() -> {
      translatedText.setText(translated);
    });
  }

  /**
   * 处理从 AudioRecorder 回调的音频数据，更新波形和状态显示
   */
  private void handleAudioData(byte[] audioData) {
    Platform.runLater(() -> {
      short[] samples = AudioUtils.convertBytesToShorts(audioData);
      if (samples.length == 0) {
        return;
      }
      double rms = AudioUtils.calculateRMS(samples);
      double db = 20 * Math.log10(rms + 1e-12);

      double sum = 0;
      for (short sample : samples) {
        sum += Math.abs(sample);
      }
      double avg = sum / samples.length;
      double volumeDb = 20 * Math.log10(avg / 32768.0 + 1e-12);

      double threshold = audioRecorder.getSilenceThreshold();
      String curStatus = (db < threshold) ? "静音" : "录音中";
      String statusText = String.format("状态: %s | RMS: %.1f dB | 平均音量: %.1f dB | 阈值: %.1f dB", curStatus, db, volumeDb, threshold);
      statusLabel.setText(statusText);

      drawWaveform(samples);
    });
  }

  /**
   * 绘制波形
   */
  private void drawWaveform(short[] samples) {
    double width = waveformCanvas.getWidth();
    double height = waveformCanvas.getHeight();
    double yCenter = height / 2;
    gc.clearRect(0, 0, width, height);
    gc.setStroke(Color.BLUE);
    gc.setLineWidth(1);
    gc.beginPath();
    for (int i = 0; i < samples.length; i++) {
      double sample = samples[i] / 32768.0;
      double x = (double) i / samples.length * width;
      double y = yCenter - (sample * yCenter);
      if (i == 0) {
        gc.moveTo(x, y);
      } else {
        gc.lineTo(x, y);
      }
    }
    gc.stroke();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
