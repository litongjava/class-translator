package com.litongjava.translator.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioUtils {
  /**
   * 将16位单声道音频字节数组转换为short数组
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
   * 计算短数组的RMS值  
   */
  public static double calculateRMS(short[] samples) {
    double sum = 0.0;
    for (short s : samples) {
      double normalized = s / 32768.0;
      sum += normalized * normalized;
    }
    return Math.sqrt(sum / samples.length);
  }
}
