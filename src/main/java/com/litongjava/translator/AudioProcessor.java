package com.litongjava.translator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.litongjava.groq.GroqSpeechClient;

import javafx.application.Platform;

/**
 * AudioProcessor：处理原始 PCM 音频数据，将其转换为 MP3（默认）或 WAV 格式后调用 GroqSpeechClient 进行转录。
 * <p>
 * 支持的格式包括：[flac mp3 mp4 mpeg mpga m4a ogg opus wav webm]，本示例默认使用 mp3 格式，
 * 但由于 Java 1.8 没有内置的 PCM->MP3 转换库，故实际转换为 WAV 格式。
 */
public class AudioProcessor {
  // 默认选择模型（可通过 UI 改变）
  public static String selectedModel = "whisper-large-v3-turbo";

  /**
   * 处理一段音频数据：
   * 1. 尝试将 PCM 数据转换为 MP3 格式（默认格式）。
   * 2. 如果 MP3 转换不可用，则降级为转换为 WAV 格式。
   * 3. 根据转换结果传参，确保 GroqSpeechClient 正确设置 MIME 类型和文件名。
   * 4. 调用 GroqSpeechClient.transcribe 进行语音转录，并在回调中更新 UI。
   *
   * @param audioData 原始 PCM 数据
   */
  public static void processAudio(byte[] audioData) {
    byte[] convertedData;
    String audioMimeType;
    String audioFileName;
    // 默认目标格式为 mp3
    try {
      // 尝试转换为 MP3（注意：Java 1.8 没有内置支持，此方法将直接抛出异常）
      convertedData = convertPcmToMp3(audioData);
      audioMimeType = "audio/mpeg";
      audioFileName = "audio.mp3";
    } catch (UnsupportedOperationException e) {
      // MP3 转换不可用，降级为转换为 WAV 格式
      convertedData = convertPcmToWav(audioData);
      audioMimeType = "audio/wav";
      audioFileName = "audio.wav";
    }

    GroqSpeechClient.transcribe(convertedData, selectedModel, audioMimeType, audioFileName, (result) -> {
      Platform.runLater(() -> {
        MainUI.updateUI(result);
      });
    });
  }

  /**
   * 尝试将 PCM 数据转换为 MP3 格式。
   * <p>
   * 由于 Java 1.8 没有内置 PCM->MP3 转换库，此方法始终抛出异常。
   *
   * @param pcmData 原始 PCM 数据
   * @return 转换后的 MP3 数据（未实际实现）
   * @throws UnsupportedOperationException 始终抛出该异常
   */
  private static byte[] convertPcmToMp3(byte[] pcmData) {
    // 如果项目中引入了第三方 MP3 编码库，此处可实现实际转换逻辑
    throw new UnsupportedOperationException("MP3 conversion not supported in Java 1.8");
  }

  /**
   * 将 PCM 数据转换为 WAV 格式。
   * <p>
   * 本示例假定 PCM 数据为 16 位、单声道、44100 Hz 的音频数据。
   *
   * @param pcmData 原始 PCM 数据
   * @return 带 WAV 头的完整音频数据
   */
  private static byte[] convertPcmToWav(byte[] pcmData) {
    int sampleRate = 44100;
    int channels = 1;
    int bitsPerSample = 16;
    int byteRate = sampleRate * channels * bitsPerSample / 8;
    int blockAlign = channels * bitsPerSample / 8;
    int dataLength = pcmData.length;
    int headerSize = 44;
    int totalDataLen = dataLength + headerSize - 8; // 文件大小 - 8

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      // 写入 RIFF 头
      out.write("RIFF".getBytes("US-ASCII"));
      out.write(intToByteArrayLE(totalDataLen));
      out.write("WAVE".getBytes("US-ASCII"));

      // 写入 fmt 子块
      out.write("fmt ".getBytes("US-ASCII"));
      out.write(intToByteArrayLE(16)); // PCM 固定为 16
      out.write(shortToByteArrayLE((short) 1)); // AudioFormat 1 表示 PCM
      out.write(shortToByteArrayLE((short) channels));
      out.write(intToByteArrayLE(sampleRate));
      out.write(intToByteArrayLE(byteRate));
      out.write(shortToByteArrayLE((short) blockAlign));
      out.write(shortToByteArrayLE((short) bitsPerSample));

      // 写入 data 子块
      out.write("data".getBytes("US-ASCII"));
      out.write(intToByteArrayLE(dataLength));
      out.write(pcmData);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return out.toByteArray();
  }

  private static byte[] intToByteArrayLE(int value) {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
  }

  private static byte[] shortToByteArrayLE(short value) {
    return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array();
  }
}
