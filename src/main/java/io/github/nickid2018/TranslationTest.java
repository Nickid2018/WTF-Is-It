package io.github.nickid2018;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;

import java.io.FileReader;
import java.io.IOException;

public class TranslationTest {

    public static void main(String[] args) throws Exception {
        String root = "D:\\Minecraft\\.minecraft\\assets";
        JsonObject nowObjects = JsonParser.parseReader(new FileReader(root + "\\indexes\\4.json")).getAsJsonObject();
        JsonObject objectObj = nowObjects.getAsJsonObject("objects");
        String zhCN = objectObj.getAsJsonObject("minecraft/lang/zh_cn.json").get("hash").getAsString();
        String zhTW = objectObj.getAsJsonObject("minecraft/lang/zh_tw.json").get("hash").getAsString();
        String zhHK = objectObj.getAsJsonObject("minecraft/lang/zh_hk.json").get("hash").getAsString();
        JsonObject zhCNObj = JsonParser.parseReader(new FileReader(root + "\\objects\\" + zhCN.substring(0, 2) + "\\" + zhCN)).getAsJsonObject();
        JsonObject zhTWObj = JsonParser.parseReader(new FileReader(root + "\\objects\\" + zhTW.substring(0, 2) + "\\" + zhTW)).getAsJsonObject();
        JsonObject zhHKObj = JsonParser.parseReader(new FileReader(root + "\\objects\\" + zhHK.substring(0, 2) + "\\" + zhHK)).getAsJsonObject();

        StringBuilder builder = new StringBuilder();
        zhCNObj.entrySet().stream()
                .filter(e -> e.getValue().getAsString().contains("纹饰"))
                .forEach(e -> {
                    String key = e.getKey();
                    String value = e.getValue().getAsString();
                    String zhTWValue = zhTWObj.get(key).getAsString();
                    String zhHKValue = zhHKObj.get(key).getAsString();
                    if (!value.equals(zhTWValue) || !value.equals(zhHKValue))
                        builder.append("Item(nil, 'zh:%s;zh-cn:%s;zh-tw:%s;zh-hk:%s;'),\n".formatted(value, value, zhTWValue, zhHKValue));
                });
        System.out.println(builder);
    }
}
