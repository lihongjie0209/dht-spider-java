package cn.lihongjie.dht.btclient.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 简单的字节缓冲区，用于处理 TCP 流式消息的累积读取。
 * 类似 PeerWireClient 的 PipedStream，但使用 ByteArrayOutputStream 简化实现。
 */
class MessageBuffer {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(65536);

    /** 写入数据到缓冲区 */
    void write(byte[] data, int offset, int length) {
        buffer.write(data, offset, length);
    }

    /** 获取缓冲区中可用的字节数 */
    int available() {
        return buffer.size();
    }

    /** 读取指定数量的字节，如果不足则返回 null */
    byte[] read(int length) {
        if (buffer.size() < length) {
            return null;
        }
        byte[] allData = buffer.toByteArray();
        byte[] result = new byte[length];
        System.arraycopy(allData, 0, result, 0, length);
        
        // 重建缓冲区，移除已读数据
        buffer.reset();
        int remaining = allData.length - length;
        if (remaining > 0) {
            buffer.write(allData, length, remaining);
        }
        return result;
    }

    /** 清空缓冲区 */
    void clear() {
        buffer.reset();
    }

    /** 获取缓冲区总大小（用于调试） */
    int size() {
        return buffer.size();
    }
}
