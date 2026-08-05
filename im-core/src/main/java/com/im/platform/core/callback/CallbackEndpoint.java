package com.im.platform.core.callback;

/**
 * 单个钩子的配置:业务是否接管、地址是什么、地址不可用时是放行还是拒绝。
 */
public class CallbackEndpoint {

    private boolean enabled = false;
    private String url;
    /** 回调地址不可用/超时时的降级策略:false=放行(fail-open,默认), true=拒绝(fail-closed)。 */
    private boolean failClosed = false;
    private int timeoutMillis = 3000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
}
