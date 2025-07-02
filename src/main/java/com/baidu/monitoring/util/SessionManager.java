package com.baidu.monitoring.util;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class SessionManager {


	/**
	 * 当前用户连接
	 */
	public static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

	/**
  服务器连接池<port, status>
   */
	public static final Map<Integer, String> serverLinkInfo = new ConcurrentHashMap<>();

	/**
     端口对应的进程<port, pid>
     */
	public static final Map<Integer, Long> portPidInfo = new ConcurrentHashMap<>();

	/**
	 * 当前服务器ip，启动进程时保存，断开长连接时使用
	 */
	public static final AtomicReference<String> nowServerIp = new AtomicReference<>("");

	/**
	 *当前最大连接数
	 */
	public static final AtomicReference<Integer> maxLink = new AtomicReference<>(3);

	/**
	 * 正在使用的用户信息<sessionKey,port>
	 */
	public static final Map<String, Integer> useLinkInfo = new ConcurrentHashMap<>();

	/** 记录每个端口何时开始空闲 */
	public static final ConcurrentMap<Integer, Long> idleSince = new ConcurrentHashMap<>();

	/** 线程安全地存储已启动过 UE 的 session*/
	public static final Set<String> startedSessions = ConcurrentHashMap.newKeySet();

	/**
	 * 正在使用的用户ip<sessionKey,ip>
	 */
	public static final Map<String, String> useIpInfo = new ConcurrentHashMap<>();

	/**
	 * 	队列中的用户信息
	 */
	public static final List<String> queueLinkInfo = new CopyOnWriteArrayList<>();

	/**
	 * 最后一次心跳时间
	 */
	public static final Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();


	/**
     待使用的port端口，有序数组，每次取第一个
     */
	// ✅ 修改此处：使用线程安全的结构替代 LinkedHashSet
	public static final Set<Integer> postSet = Collections.synchronizedSet(new LinkedHashSet<>());

	static {
		// 初始化 15010-15019
		for (int port = 16010; port <= 16018; port++) {
			postSet.add(port);
		}
	}

	/**
	端口偏移值
	 */
	public static final int offsetValue = 1000;
}
