package com.vexus2.jenkins.chatwork.jenkinschatworkplugin;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.VariableResolver;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

public class ChatworkPublisher extends Publisher {

    private final String rid;
    private final String defaultMessage;
    private final String userInfo;
    private final String symbolTask;

    private Boolean notifyOnSuccess;
    private Boolean notifyOnFail;
    private static final Pattern pattern = Pattern.compile("\\$\\{(.+)\\}|\\$(.+)\\s?");
    private AbstractBuild build;
    private String type;
    private String ids;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor" 
    @DataBoundConstructor
        public ChatworkPublisher(String rid, String defaultMessage, String userInfo, String symbolTask, Boolean notifyOnSuccess, Boolean notifyOnFail) {
            this.rid = rid;
            this.notifyOnSuccess = notifyOnSuccess;
            this.notifyOnFail = notifyOnFail;
            this.defaultMessage = (defaultMessage != null) ? defaultMessage : "";
            this.userInfo = (userInfo != null) ? userInfo : "";
            this.symbolTask = (symbolTask != null) ? symbolTask : ":bug:";
            this.type = "";
            this.ids = "";
        }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getRid() {
        return rid;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public Boolean getNotifyOnSuccess() {
        return notifyOnSuccess;
    }

    public Boolean getNotifyOnFail() {
        return notifyOnFail;
    }

    @Override
        public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {

            Boolean result = true;
            this.build = build;

            if(this.build.getResult() == Result.SUCCESS && !this.notifyOnSuccess) {
                return true;
            }
            if(this.build.getResult() == Result.FAILURE && !this.notifyOnFail) {
                return true;
            }
            try {

                String message = createMessage();

                if (message == null) return false;

                if (message.equals("$payload")) return true;

//                message = message.substring(1, message.length() + 1);

                ChatworkClient chatworkClient = new ChatworkClient(build, getDescriptor().getApikey(), getRid(), getDefaultMessage());
                if (this.type.equals("messages")) {
                    chatworkClient.sendMessage(message);
                } else if (this.type.equals("tasks")) {
                    chatworkClient.createTask(message, this.ids);
                }
            } catch (Exception e) {
                result = false;
                listener.getLogger().println(e.getMessage());
            }
            return result;
        }

    private String createMessage() throws Exception {
        String message = this.defaultMessage;
        Matcher m = pattern.matcher(message);
        while (m.find()) {
            // If ${VARNAME} match found, return that group, else return $NoWhiteSpace group
            String matches = (m.group(1) != null) ? m.group(1) : m.group(2);

            String globalValue = getValue(matches);
            if (globalValue != null) {
                message = message.replaceAll(matches, globalValue);
            }
        }
        return message;

    }

    private String getValue(String key) {
        if (key == null) {
            return null;
        } else {
            VariableResolver buildVariableResolver = build.getBuildVariableResolver();
            Object defaultValue = buildVariableResolver.resolve(key);
            return (defaultValue == null) ? "" : ("payload".equals(key)) ? analyzePayload(defaultValue.toString()) : defaultValue.toString();
        }
    }

    private String analyzePayload(String parameterDefinition) {

        JSONObject json = JSONObject.fromObject(parameterDefinition);

        StringBuilder message;
        StringBuilder taskIds;
        String chatworkId = "";
        String chatworkName = "";

        ArrayList<String> chatworkIds = new ArrayList<String>();
        ArrayList<String> chatworkNames = new ArrayList<String>();

        if (json.has("action") && "opened".equals(json.getString("action"))) {
            JSONObject pull_request = json.getJSONObject("pull_request");
            String title = pull_request.getString("title");
            String body = pull_request.getString("body");
            String url = pull_request.getString("html_url");
            String pusher = pull_request.getJSONObject("user").getString("login");

            JSONObject userInfo = JSONObject.fromObject(this.userInfo);
            JSONArray users = userInfo.getJSONArray("users");
            int usersLength = users.size();
            for (int i = 0; i < usersLength; i++) {
                JSONObject value = (JSONObject) users.get(i);
                if (body.indexOf(value.getString("github_id")) != -1) {
                    chatworkId = value.getString("chatwork_id");
                    chatworkName = value.getString("chatwork_name");
                    break;
                }
            }

            if (chatworkIds == null || chatworkIds.size() == 0 || chatworkNames == null || chatworkNames.size() == 0) {
                return null;
            } else {
                // To通知
                message = new StringBuilder().append(String.format("[To:%s] %sさん\n", chatworkId, chatworkName));
                message.append(String.format("%s pull request %s\n", pusher, title));
                message.append(url);

                this.type = "tasks";
                this.ids = chatworkId;
            }
        } else if (json.has("comment")) {
            JSONObject comment = json.getJSONObject("comment");
            String body = comment.getString("body");
            String url = comment.getString("html_url");
            String reviewer = comment.getJSONObject("user").getString("login");

            JSONObject userInfo = JSONObject.fromObject(this.userInfo);
            JSONArray users = userInfo.getJSONArray("users");
            int usersLength = users.size();
            for (int i = 0; i < usersLength; i++) {
                JSONObject value = (JSONObject) users.get(i);
                if (body.indexOf(value.getString("github_id")) != -1) {
                    chatworkIds.add(value.getString("chatwork_id"));
                    chatworkNames.add(value.getString("chatwork_name"));
                }
            }

            if (chatworkIds == null || chatworkIds.size() == 0 || chatworkNames == null || chatworkNames.size() == 0) {
                return null;
            } else {
                // To通知
                message = new StringBuilder();
                for (int i = 0; i < chatworkIds.size(); i++) {
                  message.append(String.format("[To:%s] %sさん\n", chatworkIds.get(i), chatworkNames.get(i)));
                }
                message.append(String.format("%sさんがコメントをつけました。\n", reviewer));
                message.append(url);

                if (body.indexOf(":bug:") != -1) {
                    this.type = "tasks";
                    taskIds = new StringBuilder();
                    taskIds.append(chatworkIds.get(0));
                    if (chatworkIds.size() > 1) {
                        for (int i = 1; i < chatworkIds.size(); i++) {
                            taskIds.append(",");
                            taskIds.append(chatworkIds.get(i));
                        }
                    }
                    this.ids = taskIds.toString();
                } else {
                    this.type = "messages";
                }
            }
        } else {
            return null;
        }

        return message.toString();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * Descriptor for {@link ChatworkPublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     * <p/>
     * <p/>
     * See <tt>src/main/resource/com.vexus2.jenkins.chatwork.jenkinschatworkplugin/ChatworkPublisher/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension
    // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        private String apikey;

        public String getApikey() {
            return apikey;
        }

        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "Notify the ChatWork";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            apikey = formData.getString("apikey");
            save();
            return super.configure(req, formData);
        }
    }
}
