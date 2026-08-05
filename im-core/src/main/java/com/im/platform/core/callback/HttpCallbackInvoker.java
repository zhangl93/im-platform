package com.im.platform.core.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 默认的回调实现:同步 HTTP POST。业务在自己的系统里起一个 HTTP 端点,
 * 在 application.yml 配好 URL 和 enabled=true 就接管了对应的钩子,IM 核心不需要重新部署。
 *
 * 换成异步/消息队列等其他方式,只要新写一个 CallbackInvoker 实现并替换掉这个 Bean 即可,
 * MessageWriteService 等调用方不感知具体实现(见 CallbackInvoker 的类注释)。
 */
@Component
public class HttpCallbackInvoker implements CallbackInvoker {

    private static final Logger log = LoggerFactory.getLogger(HttpCallbackInvoker.class);

    private final CallbackProperties properties;
    private final RestClient.Builder restClientBuilder;

    public HttpCallbackInvoker(CallbackProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public CallbackResult invoke(CallbackPayload payload) {
        CallbackEndpoint endpoint = properties.get(payload.type());
        if (endpoint == null || !endpoint.isEnabled() || endpoint.getUrl() == null) {
            return CallbackResult.ok();
        }

        try {
            CallbackHttpResponse response = restClientBuilder.build()
                    .post()
                    .uri(endpoint.getUrl())
                    .body(payload)
                    .retrieve()
                    .body(CallbackHttpResponse.class);

            if (response == null) {
                return CallbackResult.ok();
            }
            return response.pass()
                    ? CallbackResult.ok()
                    : CallbackResult.reject(response.rejectReason());
        } catch (Exception e) {
            log.warn("callback invoke failed, type={}, url={}, failClosed={}",
                    payload.type(), endpoint.getUrl(), endpoint.isFailClosed(), e);
            return endpoint.isFailClosed()
                    ? CallbackResult.reject("callback unavailable: " + e.getMessage())
                    : CallbackResult.ok();
        }
    }

    /** 业务回调端点的响应体约定,和 CallbackResult 字段保持一致。 */
    record CallbackHttpResponse(boolean pass, String rejectReason) {
    }
}
