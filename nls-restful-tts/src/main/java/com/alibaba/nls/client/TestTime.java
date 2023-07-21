package com.alibaba.nls.client;

import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;

public class TestTime {
    public static void main(String[] args) {
        String appKey = "ZosndNxIufNjR25s";
        String id = "LTAI5tAS7piKbU37QR7Px4Xw";
        String secret = "kMexSFfbT8GCekqHk990KGRo6f7dov";

        SpeechSynthesizerDemoTest demo = new SpeechSynthesizerDemoTest(appKey, id, secret);
        String text = "<speak>Those around him think he has no secrets</speak>";
        String musicOutFile = "/Users/shixiaoqi/Downloads/999.mp3";

        int[] times = new int[]{0,0};
        demo.process(text,musicOutFile,times);
        demo.shutdown();

        System.out.println("times====>"+times[0]+","+times[1]);
    }


}
