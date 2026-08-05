package com.im.platform.idgen;

/**
 * 发号器对外暴露的接口。业务代码(biz/msg/dfs)只依赖这个接口,不关心号是本地生成的
 * 还是以后又拆回独立服务、换成 gRPC 调用——那只需要换一个实现类,调用方代码不用动
 * (依赖倒置,天然满足开闭原则)。
 */
public interface IdGenClient {

    long generateId(String bizType);

    long generateId(String bizType, long shardKey);
}
