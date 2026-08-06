package com.im.platform.gateway.router;

/**
 * 客户端 &lt;-&gt; gateway 自研协议里,GatewayRequest.method_id 的取值约定。
 * 这是网关私有的编号方案,内部 gRPC 服务本身不关心这些数字——新增一个方法只需要在这里
 * 加一个常量,再去 MethodRegistry 里注册一行,不用改路由分发逻辑。
 *
 * 分段:1000 段 session,2000 段 biz.user,2100 段 biz.group,3000 段 msg,
 * 4000 段 sync,5000 段 status,6000 段 dfs,7000 段 push。
 */
public final class MethodIds {

    private MethodIds() {
    }

    // session
    public static final int NEGOTIATE_KEY = 1001;
    public static final int AUTHENTICATE = 1002;
    public static final int VALIDATE_SESSION = 1003;
    public static final int CLOSE_SESSION = 1004;

    // biz.user
    public static final int GET_USER = 2001;
    public static final int UPDATE_PROFILE = 2002;
    public static final int BLOCK_USER = 2003;
    public static final int GET_CONTACTS = 2004;
    public static final int SEND_FRIEND_REQUEST = 2005;
    public static final int HANDLE_FRIEND_REQUEST = 2006;
    public static final int GET_FRIEND_REQUESTS = 2007;
    public static final int REMOVE_FRIEND = 2008;

    // biz.group
    public static final int CREATE_GROUP = 2101;
    public static final int TRANSFER_OWNER = 2102;
    public static final int ADD_MEMBER = 2103;
    public static final int REMOVE_MEMBER = 2104;
    public static final int UPDATE_MEMBER_ROLE = 2105;
    public static final int GET_GROUP_INFO = 2106;
    public static final int REQUEST_JOIN_GROUP = 2107;
    public static final int HANDLE_JOIN_REQUEST = 2108;
    public static final int GET_JOIN_REQUESTS = 2109;
    public static final int UPDATE_JOIN_MODE = 2110;
    public static final int UPDATE_GROUP_MUTE_ALL = 2111;
    public static final int MUTE_MEMBER = 2112;
    public static final int LEAVE_GROUP = 2113;
    public static final int GET_MY_GROUPS = 2114;
    public static final int GET_GROUP_MEMBERS = 2115;

    // msg
    public static final int SEND_MESSAGE = 3001;
    public static final int PULL_HISTORY = 3002;
    public static final int UPDATE_READ_CURSOR = 3003;
    public static final int GET_OR_CREATE_SINGLE_CHAT = 3004;
    public static final int UPDATE_CONVERSATION_SETTING = 3005;
    public static final int GET_CONVERSATION_SETTINGS = 3006;
    public static final int GET_UNREAD_COUNT = 3007;
    public static final int RECALL_MESSAGE = 3008;

    // sync
    public static final int PULL_UPDATES = 4001;

    // status
    public static final int SET_ONLINE = 5001;
    public static final int SET_OFFLINE = 5002;
    public static final int GET_STATUS = 5003;
    public static final int BATCH_GET_STATUS = 5004;

    // dfs
    public static final int REQUEST_UPLOAD = 6001;
    public static final int COMPLETE_UPLOAD = 6002;
    public static final int GET_DOWNLOAD_URL = 6003;

    // push(离线推送设备 token 登记)
    public static final int REGISTER_PUSH_TOKEN = 7001;
    public static final int UNREGISTER_PUSH_TOKEN = 7002;

    // 网关控制帧,纯本地处理,不转发给 im-core
    public static final int HEARTBEAT = 9001;
    public static final int ACK = 9002;
    public static final int PUSH_MESSAGE = 9003; // 只用作服务端推给客户端的 method_id 标识,客户端不会主动发这个
}
