package com.alibaba.nls.client;

import com.alibaba.fastjson.JSONObject;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
/**
 * 此示例演示了
 *      录音文件识别api使用
 * (仅作演示，需用户根据实际情况实现)
 */
public class SpeechFileTransDemo {
    // 地域ID，常量内容，请勿改变
    public static final String REGIONID = "cn-shanghai";
    public static final String ENDPOINTNAME = "cn-shanghai";
    public static final String PRODUCT = "nls-filetrans";
    public static final String DOMAIN = "filetrans.cn-shanghai.aliyuncs.com";
    public static final String API_VERSION = "2018-08-17";
    public static final String POST_REQUEST_ACTION = "SubmitTask";
    public static final String GET_REQUEST_ACTION = "GetTaskResult";
    // 请求参数key，请勿改变
    public static final String KEY_APP_KEY = "appkey";
    public static final String KEY_FILE_LINK = "file_link";
    public static final String KEY_VERSION = "version";
    public static final String KEY_ENABLE_WORDS = "enable_words";
    // 响应参数key，请勿改变
    public static final String KEY_TASK = "Task";
    public static final String KEY_TASK_ID = "TaskId";
    public static final String KEY_STATUS_TEXT = "StatusText";
    public static final String KEY_RESULT = "Result";
    // 状态值，请勿改变
    public static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_QUEUEING = "QUEUEING";
    // 阿里云鉴权client
    IAcsClient client;

    public SpeechFileTransDemo(String accessKeyId, String accessKeySecret) {
        // 设置endpoint
        try {
            DefaultProfile.addEndpoint(ENDPOINTNAME, REGIONID, PRODUCT, DOMAIN);
        } catch (ClientException e) {
            e.printStackTrace();
        }
        // 创建DefaultAcsClient实例并初始化
        DefaultProfile profile = DefaultProfile.getProfile(REGIONID, accessKeyId, accessKeySecret);
        this.client = new DefaultAcsClient(profile);
    }

    public String submitFileTransRequest(String appKey, String fileLink) {
        /**
         * 1. 创建CommonRequest 设置请求参数
         */
        CommonRequest postRequest = new CommonRequest();
        // 设置域名
        postRequest.setDomain(DOMAIN);
        // 设置API的版本号，格式为YYYY-MM-DD
        postRequest.setVersion(API_VERSION);
        // 设置action
        postRequest.setAction(POST_REQUEST_ACTION);
        // 设置产品名称
        postRequest.setProduct(PRODUCT);
        /**
         * 2. 设置录音文件识别请求参数，以JSON字符串的格式设置到请求的Body中
         */
        JSONObject taskObject = new JSONObject();
        // 设置appkey
        taskObject.put(KEY_APP_KEY, appKey);
        // 设置音频文件访问链接
        taskObject.put(KEY_FILE_LINK, fileLink);
        // 新接入请使用4.0版本，已接入(默认2.0)如需维持现状，请注释掉该参数设置
        taskObject.put(KEY_VERSION, "4.0");

        // 设置是否输出词信息，默认为false，开启时需要设置version为4.0及以上
        taskObject.put(KEY_ENABLE_WORDS, true);

        // TODO 如果使用回调方式，请在task字符串中设置“enable_callback”、“callback_url”参数：
        //taskObject.put("enable_callback", true);
        //taskObject.put("callback_url", "您的回调地址，注意需要公网可访问到");

        String task = taskObject.toJSONString();
        System.out.println(task);
        // 设置以上JSON字符串为Body参数
        postRequest.putBodyParameter(KEY_TASK, task);
        // 设置为POST方式的请求
        postRequest.setMethod(MethodType.POST);
        /**
         * 3. 提交录音文件识别请求，获取录音文件识别请求任务的ID，以供识别结果查询使用
         */
        String taskId = null;
        try {
            CommonResponse postResponse = client.getCommonResponse(postRequest);
            System.err.println("提交录音文件识别请求的响应：" + postResponse.getData());
            if (postResponse.getHttpStatus() == 200) {
                JSONObject result = JSONObject.parseObject(postResponse.getData());
                String statusText = result.getString(KEY_STATUS_TEXT);
                if (statusText.equals(STATUS_SUCCESS)) {
                    taskId = result.getString(KEY_TASK_ID);
                }
            }
        } catch (ClientException e) {
            e.printStackTrace();
        }
        return taskId;
    }

    public String getFileTransResult(String taskId) {
        /**
         * 1. 创建CommonRequest 设置任务ID
         */
        CommonRequest getRequest = new CommonRequest();
        // 设置域名
        getRequest.setDomain(DOMAIN);
        // 设置API版本
        getRequest.setVersion(API_VERSION);
        // 设置action
        getRequest.setAction(GET_REQUEST_ACTION);
        // 设置产品名称
        getRequest.setProduct(PRODUCT);
        // 设置任务ID为查询参数
        getRequest.putQueryParameter(KEY_TASK_ID, taskId);
        // 设置为GET方式的请求
        getRequest.setMethod(MethodType.GET);
        /**
         * 2. 提交录音文件识别结果查询请求
         * 以轮询的方式进行识别结果的查询，直到服务端返回的状态描述为“SUCCESS”,或者为错误描述，则结束轮询。
         */
        String result = null;
        while (true) {
            try {
                CommonResponse getResponse = client.getCommonResponse(getRequest);
                System.err.println("识别查询结果：" + getResponse.getData());
                if (getResponse.getHttpStatus() != 200) {
                    break;
                }
                JSONObject rootObj = JSONObject.parseObject(getResponse.getData());
                String statusText = rootObj.getString(KEY_STATUS_TEXT);
                if (statusText.equals(STATUS_RUNNING) || statusText.equals(STATUS_QUEUEING)) {
                    // 继续轮询，注意设置轮询时间间隔
                    Thread.sleep(3000);
                }
                else {
                    // 状态信息为成功，返回识别结果；
                    if (statusText.equals(STATUS_SUCCESS)) {
                        result = rootObj.getString(KEY_RESULT);
                        // 状态信息为成功，但没有识别结果，则可能是由于文件里全是静音、噪音等导致识别为空
                        if(result == null) {
                            result = "";
                        }
                    }
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    public static void main(String args[]) throws Exception {
        if (args.length < 3) {
            System.err.println("SpeechFileTransDemo need params:  <app-key> <AccessKey Id> <AccessKey Secret>");
            System.exit(-1);
        }

        final String appKey = args[0];
        final String accessKeyId = args[1];
        final String accessKeySecret = args[2];

        // 示例路径文件
        /** 文件路径要求可以参考：https://help.aliyun.com/document_detail/90727.html?spm=a2c4g.11186623.6.581.27d84670ta8sN4#h2-u8C03u7528u9650u52362
         *
                录音文件访问权限需要为公开，URL中只能使用域名，不能使用IP地址；
                可用的URL地址如：
                    “https://aliyun-nls.oss-cn-hangzhou.aliyuncs.com/asr/fileASR/examples/nls-sample-16k.wav“
                不可用的地址如：
                    “http://127.0.0.1/sample.wav“
                    “D:\files\sample.wav”
                文件大小需控制在512MB以下；
         */
        String fileLink = "https://aliyun-nls.oss-cn-hangzhou.aliyuncs.com/asr/fileASR/examples/nls-sample-16k.wav";

        SpeechFileTransDemo demo = new SpeechFileTransDemo(accessKeyId, accessKeySecret);

        // TODO 第一步：提交录音文件识别请求，获取任务ID用于后续的识别结果轮询， 这一步只是将请求发送，并不会有识别结果
        String taskId = demo.submitFileTransRequest(appKey, fileLink);
        if (taskId != null) {
            System.out.println("录音文件识别请求成功，task_id: " + taskId);
        }
        else {
            System.out.println("录音文件识别请求失败！");
            return;
        }

        // TODO 第二步：根据任务ID轮询识别结果： 此处是向服务端轮询识别结果(或者也可以设置回调机制，等待服务端完成识别后回调自己的服务地址， 设置可以参考上面的submitFileTransRequest)
        String result = demo.getFileTransResult(taskId);
        if (result != null) {
            System.out.println("录音文件识别结果查询成功：" + result);
        }
        else {
            System.out.println("录音文件识别结果查询失败！");
        }

        System.out.println("### Game Over ###");
    }
}