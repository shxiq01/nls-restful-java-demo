package com.alibaba.nls.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SrtFormatXml {


    /**
     * 将srt格式的字幕文件转换为xml格式
     * srt字符串示例:
     * 1
     * 00:00:00,500 --> 00:00:02,133
     * He is a secretive spy
     *
     * 2
     * 00:00:02,133 --> 00:00:03,799
     * Those around him think he has no secrets
     *
     * xml 字符串示例:
     * <speak rate="300">
     * <break time="700ms"/>这是第一句话
     * </speak>
     * <speak rate="0">
     * 这是第二句话
     * </speak>
     */
    public static void translate(String fileInput, String fileOutput){
        try {
            String srtString = new String(Files.readAllBytes(Paths.get(fileInput)));
            String[] srtArray = srtString.split("\n");
            StringBuilder xmlStringBuilder = new StringBuilder();

            Integer totalTime = 0;
            Integer lastTime = 0;
            Integer rate = 0;

            Integer breakTime = 0;
            for (String srt : srtArray) {
                String text = srt.replace("\r","");

                if (text.trim().equals("")){
                    continue;
                }

                boolean isInt = text.matches("-?\\d+");
                if (isInt){
                    continue;
                }
                if (text.contains("-->")) {
                    String[] timeArray = text.split("-->");
                    String startTime = timeArray[0].trim();
                    String endTime = timeArray[1].trim();
                    String[] startTimeArray = startTime.split(":");
                    String[] endTimeArray = endTime.split(":");


                    Integer startTimeMs = Integer.parseInt(startTimeArray[0])*60*60*1000 + Integer.parseInt(startTimeArray[1])*60*1000 + Integer.parseInt(startTimeArray[2].split(",")[0])*1000+Integer.parseInt(startTimeArray[2].split(",")[1]);
                    Integer endTimeMs = Integer.parseInt(endTimeArray[0])*60*60*1000 + Integer.parseInt(endTimeArray[1])*60*1000 + Integer.parseInt(endTimeArray[2].split(",")[0])*1000+Integer.parseInt(endTimeArray[2].split(",")[1]);

                    // 两句话之间的间隔时间
                    int sleep = startTimeMs - lastTime;
                    if (sleep > 0) {
                        breakTime = sleep;
                    }else {
                        breakTime = 0;
                    }
                    lastTime = endTimeMs;
                    totalTime = endTimeMs - startTimeMs;
                } else {
                    Integer textLength = text.length();
                    Integer curTime = (int) Math.round((textLength/500.0)*60*1000);

                    System.out.println("textLength"+textLength+"curTime:"+curTime+" totalTime:"+totalTime);
                    if(curTime > totalTime) {
                        double rate1 = (double)curTime/totalTime;
                        double x = 1 - (double)1 / rate1;
                        double y = x/0.001;
                        rate = (int) Math.round(y);
                        System.out.println(" rate:"+rate);
                    }

                    xmlStringBuilder.append("<speak rate=\""+rate+"\">");

                    if(breakTime > 0) {
                        xmlStringBuilder.append("<break time=\"" + breakTime + "ms\"/>");
                    }

                    xmlStringBuilder.append( text);
                    xmlStringBuilder.append("</speak>");
                }

            }
            Files.write(Paths.get(fileOutput), xmlStringBuilder.toString().getBytes());


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) {
        String fileInput = "/Users/shixiaoqi/Downloads/7月13日.srt";
        String fileOutput = "/Users/shixiaoqi/Downloads/7月13日.xml";
        translate(fileInput, fileOutput);
    }



}
