package com.litongjava.translator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import javax.sound.sampled.*;
import com.litongjava.translator.consts.AudioConst;
import com.litongjava.translator.utils.AudioUtils;

/**
 * 录音器实现  
 * 1. 判断静音：当前音频分贝低于阈值时认为静音  
 * 2. 支持开始、暂停、停止；点击开始时保存录音文件，暂停时不保存，停止时写入文件
 */
public class AudioRecorder {
  private static final float SAMPLE_RATE = 16000;
  private static final int SAMPLE_SIZE = 16; // 16位
  private static final int SILENCE_DURATION = 1500; // 静音持续时间（毫秒）

  // 静音阈值（单位：dB），默认 -7.5 dB
  private double silenceThreshold = AudioConst.DEFAULT_SILENCE_THRESHOLD;
  private final Consumer<byte[]> dataConsumer;
  private volatile boolean running = false;
  private volatile boolean paused = false;
  private TargetDataLine line;

  // 用于分段传输给语音识别的 ByteArrayOutputStream
  private ByteArrayOutputStream segmentStream = new ByteArrayOutputStream();
  // 用于保存整个录音的 ByteArrayOutputStream（用于文件保存）
  private ByteArrayOutputStream recordingStream;

  public AudioRecorder(Consumer<byte[]> dataConsumer) {
    this.dataConsumer = dataConsumer;
  }

  /**
   * 启动录音线程
   */
  public void startRecording() {
    if (running) {
      return;
    }
    running = true;
    paused = false;
    recordingStream = new ByteArrayOutputStream(); // 初始化录音保存流
    new Thread(this::recordingLoop).start();
  }

  /**
   * 录音主循环
   */
  private void recordingLoop() {
    try {
      AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, 1, true, false);
      DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
      if (!AudioSystem.isLineSupported(info)) {
        System.err.println("当前音频线路不支持");
        running = false;
        return;
      }
      line = (TargetDataLine) AudioSystem.getLine(info);
      line.open(format);
      line.start();

      byte[] buffer = new byte[1024];
      long lastSoundTime = System.currentTimeMillis();

      while (running) {
        if (paused) {
          Thread.sleep(100);
          continue;
        }
        int count = line.read(buffer, 0, buffer.length);
        if (count > 0) {
          byte[] data = new byte[count];
          System.arraycopy(buffer, 0, data, 0, count);
          // 将数据传递给 UI 更新波形
          dataConsumer.accept(data);
          // 同时保存到录音文件（只有非暂停状态才写入）
          recordingStream.write(data, 0, count);
          // 用于判断是否静音，分段进行识别
          if (isSilence(data)) {
            if (System.currentTimeMillis() - lastSoundTime > SILENCE_DURATION) {
              // 语音结束时调用识别处理
              AudioProcessor.processAudio(segmentStream.toByteArray());
              segmentStream.reset();
            }
          } else {
            lastSoundTime = System.currentTimeMillis();
            segmentStream.write(data, 0, count);
          }
        }
      }
      // 录音结束后关闭音频线路
      if (line != null) {
        line.stop();
        line.close();
      }
      // 保存录音文件
      saveRecordingFile(format);
    } catch (LineUnavailableException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * 判断一段音频数据是否为静音
   */
  private boolean isSilence(byte[] buffer) {
    short[] samples = AudioUtils.convertBytesToShorts(buffer);
    if (samples.length == 0) {
      return true;
    }
    double rms = AudioUtils.calculateRMS(samples);
    double db = 20 * Math.log10(rms + 1e-12);
    return db < silenceThreshold;
  }

  public void setSilenceThreshold(double threshold) {
    this.silenceThreshold = threshold;
  }

  public double getSilenceThreshold() {
    return this.silenceThreshold;
  }

  public void pauseRecording() {
    paused = true;
  }

  public void resumeRecording() {
    paused = false;
  }

  public void stopRecording() {
    running = false;
  }

  public boolean isRunning() {
    return running;
  }

  public boolean isPaused() {
    return paused;
  }

  /**
   * 结束录音后，将录音数据保存为 WAV 文件
   */
  private void saveRecordingFile(AudioFormat format) {
    byte[] audioData = recordingStream.toByteArray();
    String filename = "recording_" + System.currentTimeMillis() + ".wav";
    File file = new File(filename);
    try {
      AudioUtils.saveWavFile(audioData, format, file);
      System.out.println("录音文件已保存至：" + file.getAbsolutePath());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
