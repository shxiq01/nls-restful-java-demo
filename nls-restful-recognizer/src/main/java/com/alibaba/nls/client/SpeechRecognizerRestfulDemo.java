package com.alibaba.nls.client;

import java.util.HashMap;

import com.alibaba.fastjson.JSONPath;

public class SpeechRecognizerRestfulDemo {
    private String accessToken;
    private String appkey;
    public SpeechRecognizerRestfulDemo(String appkey, String token) {
        this.appkey = appkey;
        this.accessToken = token;
    }
    public void process(String fileName, String format, int sampleRate,
                        boolean enablePunctuationPrediction,
                        boolean enableInverseTextNormalization,
                        boolean enableVoiceDetection) {
        /**
         * 设置HTTP REST POST请求
         * 1.使用http协议
         * 2.语音识别服务域名：nls-gateway.cn-shanghai.aliyuncs.com
         * 3.语音识别接口请求路径：/stream/v1/asr
         * 4.设置必须请求参数：appkey、format、sample_rate，
         * 5.设置可选请求参数：enable_punctuation_prediction、enable_inverse_text_normalization、enable_voice_detection
         */
        String url = "http://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/asr";
        String request = url;
        request = request + "?appkey=" + appkey;
        request = request + "&format=" + format;
        request = request + "&sample_rate=" + sampleRate;
        if (enablePunctuationPrediction) {
            request = request + "&enable_punctuation_prediction=" + true;
        }
        if (enableInverseTextNormalization) {
            request = request + "&enable_inverse_text_normalization=" + true;
        }
        if (enableVoiceDetection) {
            request = request + "&enable_voice_detection=" + true;
        }
        System.out.println("Request: " + request);
        /**
         * 设置HTTP 头部字段
         * 1.鉴权参数
         * 2.Content-Type：application/octet-stream
         */
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("X-NLS-Token", this.accessToken);
        headers.put("Content-Type", "application/octet-stream");
        /**
         * 发送HTTP POST请求，返回服务端的响应
         */
        long start = System.currentTimeMillis();
        String response = HttpUtil.sendPostFile(request, headers, fileName);
        System.out.println("latency = " + (System.currentTimeMillis() - start) + " ms");
        if (response != null) {
            System.out.println("Response: " + response);
            String result = JSONPath.read(response, "result").toString();
            System.out.println("识别结果：" + result);
        }
        else {
            System.err.println("识别失败!");
        }
    }
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("SpeechRecognizerRESTfulDemo need params: <token> <app-key>");
            System.exit(-1);
        }
        String token = args[0];
        String appkey = args[1];

        SpeechRecognizerRestfulDemo demo = new SpeechRecognizerRestfulDemo(appkey, token);
        //String fileName = SpeechRecognizerRestfulDemo.class.getClassLoader().getResource("./nls-sample-16k.wav").getPath();
        // TODO 重要提示： 这里用一个本地文件来模拟发送实时流数据，实际使用时，用户可以从某处实时采集或接收语音流并发送到ASR服务端
        String fileName = "./4ef5856b58fd4a4abea78c590aa16b87.pcm";
        String format = "pcm";
        int sampleRate = 8000;
        boolean enablePunctuationPrediction = true;
        boolean enableInverseTextNormalization = true;
        boolean enableVoiceDetection = false;
        demo.process(fileName, format, sampleRate, enablePunctuationPrediction, enableInverseTextNormalization, enableVoiceDetection);
    }
}