package com.vexus2.jenkins.chatwork.jenkinschatworkplugin;

import hudson.model.AbstractBuild;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;

import java.util.Date;

public class ChatworkClient {

  private final String apiKey;

  private final String channelId;

  private final AbstractBuild build;

  private final String defaultMessage;

  private static final String API_URL = "https://api.chatwork.com/v1";

  public ChatworkClient(AbstractBuild build, String apiKey, String channelId, String defaultMessage) {
      this.build = build;
      this.apiKey = apiKey;
      this.channelId = channelId;
      this.defaultMessage = defaultMessage;
  }

  public boolean sendMessage(String message) throws Exception {
      if (this.build == null || this.apiKey == null || this.channelId == null) {
          throw new Exception("API Key or Channel ID is null");
      }

      String url = API_URL + "/rooms/" + this.channelId + "/messages";
      URL obj = new URL(url);
      HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

      con.setRequestMethod("POST");
      con.setRequestProperty("X-ChatWorkToken", this.apiKey);
      con.setRequestProperty("Content-type", "application/x-www-form-urlencoded; charset=UTF-8");

      String urlParameters = "body=" + message;

      con.setDoOutput(true);
      DataOutputStream wr = new DataOutputStream(con.getOutputStream());
      wr.write(urlParameters.getBytes("utf-8"));
      wr.flush();
      wr.close();
      con.connect();

      int responseCode = con.getResponseCode();
      if (responseCode != 200) {
          throw new Exception("Response is not valid. Check your API Key or Chatwork API status. response_code = " + responseCode + ", message = " + con.getResponseMessage());
      }

      BufferedReader in = new BufferedReader(
              new InputStreamReader(con.getInputStream()));
      String inputLine;
      StringBuilder response = new StringBuilder();

      while ((inputLine = in.readLine()) != null) {
          response.append(inputLine);
      }
      in.close();

      return true;
  }

  public boolean createTask(String message, String ids) throws Exception {
      if (this.build == null || this.apiKey == null || this.channelId == null) {
          throw new Exception("API Key or Channel ID is null");
      }

      String url = API_URL + "/rooms/" + this.channelId + "/tasks";
      URL obj = new URL(url);
      HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

      con.setRequestMethod("POST");
      con.setRequestProperty("X-ChatWorkToken", this.apiKey);
      con.setRequestProperty("Content-type", "application/x-www-form-urlencoded; charset=UTF-8");

      // Taskの期限の設定
      // 暫定的に期限を当日に設定する(ChatWorkの期限は日付までなので、UnixTimeStampの値を1000で割る必要がある)
      long limit = System.currentTimeMillis() / 1000;

      String urlParameters = "body=" + message + "&limit=" + limit + "&to_ids=" + ids;

      con.setDoOutput(true);
      DataOutputStream wr = new DataOutputStream(con.getOutputStream());
      wr.write(urlParameters.getBytes("utf-8"));
      wr.flush();
      wr.close();
      con.connect();

      int responseCode = con.getResponseCode();
      if (responseCode != 200) {
          throw new Exception("Response is not valid. Check your API Key or Chatwork API status. response_code = " + responseCode + ", message = " + con.getResponseMessage());
      }

      BufferedReader in = new BufferedReader(
              new InputStreamReader(con.getInputStream()));
      String inputLine;
      StringBuilder response = new StringBuilder();

      while ((inputLine = in.readLine()) != null) {
          response.append(inputLine);
      }
      in.close();

      return true;
  }
}
