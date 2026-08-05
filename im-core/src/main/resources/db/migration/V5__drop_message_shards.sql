-- 消息存储改成固定用 MongoDB(不再有 MySQL/MongoDB 可选切换),V3 建的 t_message_0~3
-- 分片表不再被应用代码使用。Flyway 迁移文件是只增不改的(V3 已经在开发库里跑过,不能回头
-- 改内容,校验和对不上会导致后续迁移全部失败),所以用一条新迁移显式删掉这几张表,
-- 而不是回去改 V3。
-- 这仍然是全新起步的项目,t_message_0~3 里没有需要迁移走的历史数据,直接删表;
-- 真实场景下从 MySQL 切到 MongoDB 需要先双写迁移数据,再切读流量,不能直接 DROP。

DROP TABLE IF EXISTS t_message_0;
DROP TABLE IF EXISTS t_message_1;
DROP TABLE IF EXISTS t_message_2;
DROP TABLE IF EXISTS t_message_3;
