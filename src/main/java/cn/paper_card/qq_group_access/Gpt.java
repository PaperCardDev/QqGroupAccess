package cn.paper_card.qq_group_access;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

class Gpt {

    private final @NotNull Gson gson;

    Gpt() {
        this.gson = new Gson();
    }

    private static void close(@NotNull InputStream inputStream, @NotNull InputStreamReader reader) throws IOException {

        try {
            reader.close();
        } catch (IOException e) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }

            throw e;
        }


        inputStream.close();
    }

    @NotNull String request(@NotNull String msg, long id) throws IOException {
        final String key1 = "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";
        final String key2 = "xc5pg74LdBZXy4sHjiczPVJFey";
        final String url = "https://api.lolimi.cn/api/ai/c1?msg=%s&y=%s&model=gpt-3.5&id=%d&key=%s".formatted(
                URLEncoder.encode(msg, StandardCharsets.UTF_8), key1, id, key2
        );


        final URL url1;
        try {
            url1 = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        final HttpsURLConnection connection = (HttpsURLConnection) url1.openConnection();

        final InputStream inputStream = connection.getInputStream();
        final InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        final JsonObject jsonObject;

        jsonObject = this.gson.fromJson(inputStreamReader, JsonObject.class);

        final String output = jsonObject.get("data").getAsJsonObject().get("output").getAsString();

        System.out.println(output);

        close(inputStream, inputStreamReader);


        connection.disconnect();

        return output;

    }
}
