package com.alibaba.nls.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 此示例演示了：
 *      语音合成API调用。
 *      动态获取token。获取Token具体操作，请参见：https://help.aliyun.com/document_detail/450514.html
 *      流式合成TTS。
 *      首包延迟计算。
 */
public class SpeechSynthesizerDemoTest {
    private static final Logger logger = LoggerFactory.getLogger(SpeechSynthesizerDemoTest.class);
    private static long startTime;
    private String appKey;
    NlsClient client;
    public SpeechSynthesizerDemoTest(String appKey, String accessKeyId, String accessKeySecret) {
        this.appKey = appKey;
        //应用全局创建一个NlsClient实例，默认服务地址为阿里云线上服务地址。
        //获取token，使用时注意在accessToken.getExpireTime()过期前再次获取。
        AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
        try {
            accessToken.apply();
            System.out.println("get token: " + accessToken.getToken() + ", expire time: " + accessToken.getExpireTime());
            client = new NlsClient(accessToken.getToken());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public SpeechSynthesizerDemoTest(String appKey, String accessKeyId, String accessKeySecret, String url) {
        this.appKey = appKey;
        AccessToken accessToken = new AccessToken(accessKeyId, accessKeySecret);
        try {
            accessToken.apply();
            System.out.println("get token: " + accessToken.getToken() + ", expire time: " + accessToken.getExpireTime());
            if(url.isEmpty()) {
                client = new NlsClient(accessToken.getToken());
            }else {
                client = new NlsClient(url, accessToken.getToken());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static SpeechSynthesizerListener getSynthesizerListener(String musicOutFile,int[] times) {
        SpeechSynthesizerListener listener = null;
        try {
            listener = new SpeechSynthesizerListener() {
                File f=new File(musicOutFile);
                FileOutputStream fout = new FileOutputStream(f);
                private boolean firstRecvBinary = true;
                //语音合成结束
                @Override
                public void onComplete(SpeechSynthesizerResponse response) {
                    //调用onComplete时表示所有TTS数据已接收完成，因此为整个合成数据的延迟。该延迟可能较大，不一定满足实时场景。
                    System.out.println("name: " + response.getName() +
                            ", status: " + response.getStatus()+
                            ", output file :"+f.getAbsolutePath()+
                            ", response : " + response.getObject("subtitles")
                    );
                }
                //语音合成的语音二进制数据
                @Override
                public void onMessage(ByteBuffer message) {
                    try {
                        if(firstRecvBinary) {
                            //计算首包语音流的延迟，收到第一包语音流时，即可以进行语音播放，以提升响应速度（特别是实时交互场景下）。
                            firstRecvBinary = false;
                            long now = System.currentTimeMillis();
                            logger.info("tts first latency : " + (now - SpeechSynthesizerDemoTest.startTime) + " ms");
                        }
                        byte[] bytesArray = new byte[message.remaining()];
                        message.get(bytesArray, 0, bytesArray.length);
                        fout.write(bytesArray);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onMetaInfo(SpeechSynthesizerResponse response) {
                    // 调用onMetaInfo时表示返回字级别时间戳
                    System.out.println("name: " + response.getName() + ", task_id: " + response.getTaskId());
                    JSONArray subtitles = (JSONArray)response.getObject("subtitles");
                    List<Map> subtitleList = subtitles.toJavaList(Map.class);

                    times[0] = (int)subtitleList.get(0).get("begin_time");
                    times[1] = (int)subtitleList.get(subtitleList.size()-1).get("end_time");
                    System.out.println("begin_time: " + times[0] + ", end_time: " + times[1]);

//                    for (Map word : subtitleList) {
//                        System.out.println("current subtitle: " + word);
//                    }
                }
                @Override
                public void onFail(SpeechSynthesizerResponse response){
                    //task_id是调用方和服务端通信的唯一标识，当遇到问题时需要提供task_id以便排查。
                    System.out.println(
                            "task_id: " + response.getTaskId() +
                                    //状态码 20000000 表示识别成功
                                    ", status: " + response.getStatus() +
                                    //错误信息
                                    ", status_text: " + response.getStatusText());
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
        return listener;
    }
    public void process(String text,String musicOutFile,int[] times) {
        SpeechSynthesizer synthesizer = null;
        try {
            //创建实例，建立连接。
            synthesizer = new SpeechSynthesizer(client, getSynthesizerListener(musicOutFile,times));
            synthesizer.setAppKey(appKey);
            //设置返回音频的编码格式
            synthesizer.setFormat(OutputFormatEnum.MP3);
            //设置返回音频的采样率
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            //发音人
            synthesizer.setVoice("abby");
            //语调，范围是-500~500，可选，默认是0。
//            synthesizer.setPitchRate(100);
            //语速，范围是-500~500，默认是0。
//            synthesizer.setSpeechRate(100);
            //设置用于语音合成的文本
            synthesizer.setText(text);
            // 是否开启字幕功能（返回相应文本的时间戳），默认不开启，需要注意并非所有发音人都支持该参数。
            synthesizer.addCustomedParam("enable_subtitle", true);
            //此方法将以上参数设置序列化为JSON格式发送给服务端，并等待服务端确认。
            long start = System.currentTimeMillis();
            synthesizer.start();
            logger.info("tts start latency " + (System.currentTimeMillis() - start) + " ms");
            SpeechSynthesizerDemoTest.startTime = System.currentTimeMillis();
            //等待语音合成结束
            synthesizer.waitForComplete();
            logger.info("tts stop latency " + (System.currentTimeMillis() - start) + " ms");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //关闭连接
            if (null != synthesizer) {
                synthesizer.close();
            }
        }
    }
    public void shutdown() {
        client.shutdown();
    }
    public static void main(String[] args) throws Exception {
        String appKey = "";
        String id = "";
        String secret = "";

        SpeechSynthesizerDemoTest demo = new SpeechSynthesizerDemoTest(appKey, id, secret);
//        String fileInput = "/Users/shixiaoqi/Downloads/7月13日.srt";
        String fileOutput = "/Users/shixiaoqi/Downloads/7月13日.xml";
//        SrtFormatXml.translate(fileInput, fileOutput);

//        String text = new String(Files.readAllBytes(Paths.get(fileOutput)));
        String text = "<speak>请闭上眼睛休息一下<break time=\"500ms\"/>好了，请睁开眼睛。</speak><speak>请闭上眼睛休息一下<break time=\"500ms\"/>好了，请睁开眼睛。</speak>";
//        String text = "<speak><break time=\"500ms\"/></speak><speak rate=\"352\"><s>He is a secretive spy</s></speak><speak rate=\"352\"><s></s></speak>";
        String musicOutFile = "/Users/shixiaoqi/Downloads/666.mp3";

        int[] times = new int[]{0,0};
        demo.process(text,musicOutFile,times);
        demo.shutdown();
    }
}