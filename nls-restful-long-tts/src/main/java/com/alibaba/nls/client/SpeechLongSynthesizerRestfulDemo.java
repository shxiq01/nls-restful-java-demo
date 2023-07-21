package com.alibaba.nls.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 此示例演示了长文本语音合成的使用方式
 * 支持调用方主动轮询和服务端回调两种方式
 */
public class SpeechLongSynthesizerRestfulDemo {
    private static Logger logger = LoggerFactory.getLogger(SpeechLongSynthesizerRestfulDemo.class);
    private String accessToken;
    private String appkey;
    public SpeechLongSynthesizerRestfulDemo(String appkey, String token) {
        this.appkey = appkey;
        this.accessToken = token;
    }

    public void processPOSTRequest(String text, String format, int sampleRate, String voice) {
        // 注意： 和原语音合成接口的url并不一样，混用url则可能出错
        String url = "https://nls-gateway.cn-shanghai.aliyuncs.com/rest/v1/tts/async";

        // 拼接Http Post请求的消息体内容
        JSONObject context = new JSONObject();
        // device_id设置，可以设置为自定义字符串或者设备信息id
        context.put("device_id", "my_device_id");

        JSONObject header = new JSONObject();
        // 必选：设置你的appkey
        header.put("appkey", appkey);
        // 必选：设置你的token
        header.put("token", accessToken);

        // voice 发音人，可选，默认是xiaoyun
        // volume 音量，范围是0~100，可选，默认50
        // speech_rate 语速，范围是-500~500，可选，默认是0
        // pitch_rate 语调，范围是-500~500，可选，默认是0
        JSONObject tts = new JSONObject();
        tts.put("text", text);
        // 设置发音人
        tts.put("voice", voice);
        // 设置编码格式
        tts.put("format", format);
        // 设置采样率
        tts.put("sample_rate", sampleRate);
        // 设置声音大小，可选
        //tts.put("volume", 100);
        // 设置语速，可选
        //tts.put("speech_rate", 200);

        JSONObject payload = new JSONObject();
        // 可选，是否设置回调(enable_notify为true且notify_url有效)，如果设置，则服务端在完成长文本语音合成之后回调用用户此处设置的回调接口，将请求状态推送给用户侧
        payload.put("enable_notify", false);
        payload.put("notify_url", "http://123.com");
        payload.put("tts_request", tts);
        logger.info("==" + payload.toJSONString());

        JSONObject json = new JSONObject();
        json.put("context", context);
        json.put("header", header);
        json.put("payload", payload);

        String bodyContent = json.toJSONString();
        logger.info("POST Body Content: " + bodyContent);

        // 发起请求
        RequestBody reqBody = RequestBody.create(MediaType.parse("application/json"), bodyContent);
        Request request = new Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(reqBody)
            .build();
        try {
            OkHttpClient client = new OkHttpClient();
            Response response = client.newCall(request).execute();
            String contentType = response.header("Content-Type");
            System.out.println("contentType = " + contentType);

            // 获取结果，并根据返回进一步进行处理
            String result = response.body().string();
            response.close();
            System.out.println("result = " + result);
            JSONObject resultJson = JSON.parseObject(result);
            if(resultJson.containsKey("error_code") && resultJson.getIntValue("error_code") == 20000000) {
                logger.info("Request Success! task_id = " + resultJson.getJSONObject("data").getString("task_id"));
                String task_id = resultJson.getJSONObject("data").getString("task_id");
                String request_id = resultJson.getString("request_id");

                /// 可选：轮询检查服务端的合成状态，该轮询操作非必须，如果设置了回调url(enable_notify为true且notify_url有效)，则服务端会在合成完成后主动回调
                waitLoop4Complete(url, appkey, accessToken, task_id, request_id);
            }else {
                logger.error("Request Error: status=" + resultJson.getIntValue("status")
                    + ", error_code=" + resultJson.getIntValue("error_code")
                    + ", error_message=" + resultJson.getString("error_message"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /// 根据特定信息轮询检查某个请求在服务端的合成状态，轮询操作非必须，如果设置了回调url，则服务端会在合成完成后主动回调
    private void waitLoop4Complete(String url, String appkey, String token, String task_id, String request_id) {
        String fullUrl = url + "?appkey=" + appkey + "&task_id=" + task_id + "&token=" + token + "&request_id=" + request_id;
        System.out.println("query url = " + fullUrl);
        while(true) {
            Request request = new Request.Builder().url(fullUrl).get().build();
            try {
                OkHttpClient client = new OkHttpClient();
                Response response = client.newCall(request).execute();
                String result = response.body().string();
                response.close();
                System.out.println("waitLoop4Complete = " + result);
                JSONObject resultJson = JSON.parseObject(result);
                if(resultJson.containsKey("error_code")
                    && resultJson.getIntValue("error_code") == 20000000
                    && resultJson.containsKey("data")
                    && resultJson.getJSONObject("data").getString("audio_address") != null) {
                    logger.info("Tts Finished! task_id = " + resultJson.getJSONObject("data").getString("task_id"));
                    logger.info("Tts Finished! audio_address = " + resultJson.getJSONObject("data").getString("audio_address"));
                    break;
                }else {
                    logger.info("Tts Queuing...");
                }
                // 每隔3秒钟轮询一次状态
                Thread.sleep(3000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("SpeechLongSynthesizerRestfulDemo need params: <token> <app-key>");
            System.exit(-1);
        }
        String token = args[0];
        String appkey = args[1];

        SpeechLongSynthesizerRestfulDemo demo = new SpeechLongSynthesizerRestfulDemo(appkey, token);
        String text = "鲁迅：我家的后面有一个很大的园，相传叫作百草园。现在是早已并屋子一起卖给朱文公的子孙了，连那最末次的相见也已经隔了七八年，其中似乎确凿只有一些野草；但那时却是我的乐园。";

        String format = "wav";
        int sampleRate = 16000;
        String voice = "siyue";
        demo.processPOSTRequest(text, format, sampleRate, voice);
    }
}