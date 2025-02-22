package com.litongjava.translator.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioUtils {

  /**
   * 将 16 位单声道音频字节数组转换为 short 数组
   */
  public static short[] convertBytesToShorts(byte[] audioData) {
    if (audioData.length < 2) {
      return new short[0];
    }
    if (audioData.length % 2 != 0) {
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
   * 计算样本数组的 RMS 值
   */
  public static double calculateRMS(short[] samples) {
    double sum = 0.0;
    for (short s : samples) {
      double normalized = s / 32768.0;
      sum += normalized * normalized;
    }
    return Math.sqrt(sum / samples.length);
  }

  /**
   * 将 PCM 数据保存为 WAV 文件（包含 WAV 头）
   */
  public static void saveWavFile(byte[] audioData, javax.sound.sampled.AudioFormat format, File file) throws IOException {
    try (FileOutputStream fos = new FileOutputStream(file)) {
      int channels = format.getChannels();
      int sampleRate = (int) format.getSampleRate();
      int bitsPerSample = format.getSampleSizeInBits();
      int byteRate = sampleRate * channels * bitsPerSample / 8;
      int dataSize = audioData.length;
      int chunkSize = 36 + dataSize;

      // 写入 RIFF 头
      fos.write("RIFF".getBytes());
      fos.write(intToByteArrayLE(chunkSize));
      fos.write("WAVE".getBytes());

      // fmt 子块
      fos.write("fmt ".getBytes());
      fos.write(intToByteArrayLE(16)); // PCM 的子块大小
      fos.write(shortToByteArrayLE((short) 1)); // PCM 格式
      fos.write(shortToByteArrayLE((short) channels));
      fos.write(intToByteArrayLE(sampleRate));
      fos.write(intToByteArrayLE(byteRate));
      fos.write(shortToByteArrayLE((short) (channels * bitsPerSample / 8))); // block align
      fos.write(shortToByteArrayLE((short) bitsPerSample));

      // data 子块
      fos.write("data".getBytes());
      fos.write(intToByteArrayLE(dataSize));
      fos.write(audioData);
    }
  }

  private static byte[] intToByteArrayLE(int value) {
    return new byte[] {
      (byte) (value & 0xff),
      (byte) ((value >> 8) & 0xff),
      (byte) ((value >> 16) & 0xff),
      (byte) ((value >> 24) & 0xff)
    };
  }

  private static byte[] shortToByteArrayLE(short value) {
    return new byte[] {
      (byte) (value & 0xff),
      (byte) ((value >> 8) & 0xff)
    };
  }
}
