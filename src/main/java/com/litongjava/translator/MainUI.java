package com.litongjava.translator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
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
    grid.setHgap(10);
    grid.setVgap(10);

    // 翻译结果
    grid.add(new Label("翻译结果:"), 0, 0);
    translatedText.setEditable(false);
    translatedText.setWrapText(true);
    grid.add(translatedText, 1, 0);

    // 静音阈值设置
    Slider thresholdSlider = new Slider(-40, 20, -8);
    thresholdSlider.setBlockIncrement(1);
    thresholdSlider.setMajorTickUnit(1);
    thresholdSlider.setMinorTickCount(1);
    thresholdSlider.setSnapToTicks(true);
    thresholdSlider.setShowTickLabels(true);
    thresholdSlider.setShowTickMarks(true);
    grid.add(new Label("静音阈值:"), 0, 1);
    grid.add(thresholdSlider, 1, 1);

    // 状态显示 (移动到波形上方)
    grid.add(statusLabel, 1, 2);

    // 波形显示 (放在更下面一行)
    grid.add(new Label("波形显示:"), 0, 3);
    waveformCanvas = new Canvas(800, 150);
    gc = waveformCanvas.getGraphicsContext2D();
    grid.add(waveformCanvas, 1, 3);

    Scene scene = new Scene(grid, 800, 600);
    primaryStage.setScene(scene);
    primaryStage.show();

    // 初始化录音器
    audioRecorder = new AudioRecorder(this::handleAudioData);
    thresholdSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
      audioRecorder.setSilenceThreshold(newVal.intValue());
    });

    new Thread(() -> audioRecorder.startRecording()).start();
  }

  public static void updateUI(String translated) {
    Platform.runLater(() -> {
      translatedText.setText(translated);
    });
  }

  private void handleAudioData(byte[] audioData) {
    Platform.runLater(() -> {
      // 计算分贝值 (RMS -> dB)
      double rms = calculateRMS(audioData);
      double db = 20 * Math.log10(rms + 1e-12);

      // 把数据转换为 short[]
      short[] samples = convertBytesToShorts(audioData);
      if (samples.length == 0) {
        return;
      }

      // 计算“音量”(基于平均幅度)
      double sum = 0;
      for (short sample : samples) {
        sum += Math.abs(sample);
      }
      double avg = sum / samples.length;
      double volumeDb = 20 * Math.log10(avg / 32768.0 + 1e-12);

      // 更新状态：包含录音/静音、RMS dB、平均幅度 dB 和静音阈值
      String status = db < audioRecorder.getSilenceThreshold() ? "静音" : "录音中";
      String string = "状态: %s | RMS分贝: %.1f dB | 平均音量: %.1f dB | 阈值: %d dB";
      String statusText = String.format(string, status, db, volumeDb, audioRecorder.getSilenceThreshold());
      statusLabel.setText(statusText);

      // 绘制波形
      drawWaveform(samples);
    });
  }

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

  private short[] convertBytesToShorts(byte[] audioData) {
    if (audioData.length % 2 != 0) {
      // 如果长度是奇数，去掉最后一个字节
      byte[] adjusted = new byte[audioData.length - 1];
      System.arraycopy(audioData, 0, adjusted, 0, adjusted.length);
      audioData = adjusted;
    }
    short[] samples = new short[audioData.length / 2];
    ByteBuffer bb = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN);
    bb.asShortBuffer().get(samples);
    return samples;
  }

  /**
   * 计算字节数组的 RMS
   */
  private double calculateRMS(byte[] buffer) {
    double sum = 0.0;
    for (byte b : buffer) {
      double sample = b / 128.0; // 转为 [-1,1)
      sum += sample * sample;
    }
    return Math.sqrt(sum / buffer.length);
  }
}
