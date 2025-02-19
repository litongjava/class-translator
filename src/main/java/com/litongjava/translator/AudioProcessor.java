package com.litongjava.translator;

public class AudioProcessor {
  public static void processAudio(byte[] audioData) {
    // arc
    String pythonResult = asr(audioData);

    // 调用翻译
    String translation = Translator.translateToChinese(pythonResult);

    // 更新UI
    MainUI.updateUI(pythonResult);
  }

  private static String asr(byte[] audioData) {
    return "1234";
  }
}