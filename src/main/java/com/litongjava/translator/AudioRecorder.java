package com.litongjava.translator;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import com.litongjava.translator.consts.AudioConst;
import com.litongjava.translator.utils.AudioUtils;

/**
 * 录音器实现  
 * 
 * 1. 静音的判断：当前音频的分贝低于设定的阈值（默认 -7.5 dB）时认为是静音  
 * 2. 增加了开始、暂停、停止功能，分别由外部按钮控制  
 */
public class AudioRecorder {
  private static final float SAMPLE_RATE = 16000;
  private static final int SAMPLE_SIZE = 16; // 16位单声道
  private static final int SILENCE_DURATION = 1500; // 静音持续时间(ms)

  // 静音阈值（单位：分贝），默认 -7.5 dB
  private double silenceThreshold = AudioConst.DEFAULT_SILENCE_THRESHOLD;
  private final Consumer<byte[]> dataConsumer;

  // 录音控制标志
  private volatile boolean running = false;
  private volatile boolean paused = false;
  private TargetDataLine line;

  public AudioRecorder(Consumer<byte[]> dataConsumer) {
    this.dataConsumer = dataConsumer;
  }

  /**
   * 启动录音线程  
   * 如果已运行，则不会重复启动
   */
  public void startRecording() {
    if (running) {
      return;
    }
    running = true;
    paused = false;
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
        System.err.println("Line not supported");
        running = false;
        return;
      }

      line = (TargetDataLine) AudioSystem.getLine(info);
      line.open(format);
      line.start();

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      long lastSoundTime = System.currentTimeMillis();

      while (running) {
        if (paused) {
          // 暂停时，不读取数据，等待恢复
          Thread.sleep(100);
          continue;
        }
        int count = line.read(buffer, 0, buffer.length);
        if (count > 0) {
          byte[] data = new byte[count];
          System.arraycopy(buffer, 0, data, 0, count);
          // 将数据传给UI更新波形和状态
          dataConsumer.accept(data);

          // 判断是否静音
          if (isSilence(data)) {
            // 如果静音持续时间超过设置，则认为一句结束
            if (System.currentTimeMillis() - lastSoundTime > SILENCE_DURATION) {
              AudioProcessor.processAudio(out.toByteArray());
              out.reset();
            }
          } else {
            // 有声音则更新最后声音的时间戳
            lastSoundTime = System.currentTimeMillis();
            out.write(buffer, 0, count);
          }
        }
      }
      // 录音停止时关闭数据线
      if (line != null) {
        line.stop();
        line.close();
      }
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
      return true; // 无数据视为静音
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

  // 控制接口
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
}
