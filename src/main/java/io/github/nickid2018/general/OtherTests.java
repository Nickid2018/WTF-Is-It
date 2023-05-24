package io.github.nickid2018.general;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

public class OtherTests {

    public static void main(String[] args) throws Exception {
        Field[] fields = HttpStatus.class.getDeclaredFields();
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            for (Field field : fields) {
                if (!field.getName().startsWith("SC") || field.getType() != int.class)
                    continue;
                int code = field.getInt(null);
                HttpUriRequest request = new HttpGet("https://http.cat/" + code);
                client.execute(request, response -> {
                    HttpEntity entity = response.getEntity();
                    File file = new File("D:\\icalingua\\Nickid2018\\stickers\\http.cat", String.valueOf(code));
                    try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                        entity.writeTo(fileOutputStream);
                    }
                    return null;
                });
            }
        }
    }
}
