package com.litongjava.translator;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public class AudioRecorder {
  private static final float SAMPLE_RATE = 16000;
  private static final int SAMPLE_SIZE = 16;
  private static final int SILENCE_DURATION = 1500; // 静音持续时间(ms)

  //修改静音阈值单位为分贝
  private AtomicInteger silenceThreshold = new AtomicInteger(-8); // 默认-40dB

  private final Consumer<byte[]> dataConsumer;

  public AudioRecorder(Consumer<byte[]> dataConsumer) {
    this.dataConsumer = dataConsumer;
  }

  public void startRecording() {
    try {
      AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, 1, true, false);
      DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

      if (!AudioSystem.isLineSupported(info)) {
        System.err.println("Line not supported");
        return;
      }

      TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
      line.open(format);
      line.start();

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      long lastSoundTime = System.currentTimeMillis();

      while (true) {
        int count = line.read(buffer, 0, buffer.length);
        if (count > 0) {
          // 传递音频数据到UI
          byte[] data = new byte[count];
          System.arraycopy(buffer, 0, data, 0, count);
          dataConsumer.accept(data);

          if (isSilence(buffer)) {
            if (System.currentTimeMillis() - lastSoundTime > SILENCE_DURATION) {
              AudioProcessor.processAudio(out.toByteArray());
              out.reset();
            }
          } else {
            lastSoundTime = System.currentTimeMillis();
            out.write(buffer, 0, count);
          }
        }
      }
    } catch (LineUnavailableException e) {
      e.printStackTrace();
    }
  }

  public void setSilenceThreshold(int threshold) {
    silenceThreshold.set(threshold);
  }

  private boolean isSilence(byte[] buffer) {
    double rms = calculateRMS(buffer);
    double db = 20 * Math.log10(rms + 1e-12);
    return db < silenceThreshold.get();
  }

  private double calculateRMS(byte[] buffer) {
    double sum = 0.0;
    for (byte b : buffer) {
      double sample = b / 128.0; // 转换为[-1,1)范围
      sum += sample * sample;
    }
    return Math.sqrt(sum / buffer.length);
  }

  public int getSilenceThreshold() {
    return silenceThreshold.get();
  }
}