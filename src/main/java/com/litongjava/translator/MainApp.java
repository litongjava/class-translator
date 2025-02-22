package com.litongjava.translator;

import com.litongjava.tio.utils.environment.EnvUtils;

public class MainApp {
  public static void main(String[] args) {
    EnvUtils.load();
    MainUI.launch(MainUI.class, args);
  }
}
