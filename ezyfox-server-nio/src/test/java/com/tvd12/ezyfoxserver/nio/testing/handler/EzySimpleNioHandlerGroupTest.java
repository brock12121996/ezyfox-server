package com.tvd12.ezyfoxserver.nio.testing.handler;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

import org.testng.annotations.Test;

import com.tvd12.ezyfox.codec.EzyByteToObjectDecoder;
import com.tvd12.ezyfox.codec.EzyMessage;
import com.tvd12.ezyfox.codec.EzyMessageHeader;
import com.tvd12.ezyfox.codec.EzySimpleMessage;
import com.tvd12.ezyfox.codec.EzySimpleMessageHeader;
import com.tvd12.ezyfox.concurrent.EzyExecutors;
import com.tvd12.ezyfox.factory.EzyEntityFactory;
import com.tvd12.ezyfoxserver.EzySimpleServer;
import com.tvd12.ezyfoxserver.constant.EzyCommand;
import com.tvd12.ezyfoxserver.constant.EzyConnectionType;
import com.tvd12.ezyfoxserver.constant.EzyTransportType;
import com.tvd12.ezyfoxserver.context.EzySimpleServerContext;
import com.tvd12.ezyfoxserver.entity.EzyAbstractSession;
import com.tvd12.ezyfoxserver.nio.builder.impl.EzyHandlerGroupBuilderFactoryImpl;
import com.tvd12.ezyfoxserver.nio.entity.EzyNioSession;
import com.tvd12.ezyfoxserver.nio.factory.EzyHandlerGroupBuilderFactory;
import com.tvd12.ezyfoxserver.nio.handler.EzySimpleNioHandlerGroup;
import com.tvd12.ezyfoxserver.nio.wrapper.impl.EzyNioSessionManagerImpl;
import com.tvd12.ezyfoxserver.service.impl.EzySimpleSessionTokenGenerator;
import com.tvd12.ezyfoxserver.setting.EzySimpleSettings;
import com.tvd12.ezyfoxserver.setting.EzySimpleStreamingSetting;
import com.tvd12.ezyfoxserver.socket.EzyBlockingSessionTicketsQueue;
import com.tvd12.ezyfoxserver.socket.EzyBlockingSocketStreamQueue;
import com.tvd12.ezyfoxserver.socket.EzyChannel;
import com.tvd12.ezyfoxserver.socket.EzyPacket;
import com.tvd12.ezyfoxserver.socket.EzySessionTicketsQueue;
import com.tvd12.ezyfoxserver.socket.EzySimplePacket;
import com.tvd12.ezyfoxserver.socket.EzySimpleSocketRequestQueues;
import com.tvd12.ezyfoxserver.socket.EzySocketRequestQueues;
import com.tvd12.ezyfoxserver.socket.EzySocketStreamQueue;
import com.tvd12.ezyfoxserver.statistics.EzySimpleStatistics;
import com.tvd12.ezyfoxserver.statistics.EzyStatistics;
import com.tvd12.ezyfoxserver.wrapper.EzySessionManager;
import com.tvd12.test.base.BaseTest;

public class EzySimpleNioHandlerGroupTest extends BaseTest {

	@SuppressWarnings("rawtypes")
	@Test
	public void test() throws Exception {
		EzySessionTicketsQueue socketSessionTicketsQueue = new EzyBlockingSessionTicketsQueue();
		EzySessionTicketsQueue webSocketSessionTicketsQueue = new EzyBlockingSessionTicketsQueue();
		EzyStatistics statistics = new EzySimpleStatistics();
		EzyHandlerGroupBuilderFactory handlerGroupBuilderFactory = EzyHandlerGroupBuilderFactoryImpl.builder()
				.statistics(statistics)
				.socketSessionTicketsQueue(socketSessionTicketsQueue)
				.webSocketSessionTicketsQueue(webSocketSessionTicketsQueue)
				.build();
		EzySimpleSettings settings = new EzySimpleSettings();
		EzySimpleStreamingSetting streaming = settings.getStreaming();
		streaming.setEnable(true);
		EzySimpleServer server = new EzySimpleServer();
		server.setSettings(settings);
		EzySimpleServerContext serverContext = new EzySimpleServerContext();
		serverContext.setServer(server);
		serverContext.init();
		
		EzySessionManager sessionManager = EzyNioSessionManagerImpl.builder()
				.tokenGenerator(new EzySimpleSessionTokenGenerator())
				.build();
		EzyChannel channel = mock(EzyChannel.class);
		when(channel.isConnected()).thenReturn(true);
		when(channel.getConnection()).thenReturn(SocketChannel.open());
		when(channel.getConnectionType()).thenReturn(EzyConnectionType.SOCKET);
		EzyNioSession session = (EzyNioSession) sessionManager.provideSession(channel);
		ExEzyByteToObjectDecoder decoder = new ExEzyByteToObjectDecoder();
		ExecutorService codecThreadPool = EzyExecutors.newFixedThreadPool(1, "codec");
		ExecutorService statsThreadPool = EzyExecutors.newFixedThreadPool(1, "stats");
		EzySocketRequestQueues requestQueues = new EzySimpleSocketRequestQueues();
		
		EzySimpleNioHandlerGroup group = (EzySimpleNioHandlerGroup) handlerGroupBuilderFactory.newBuilder(EzyConnectionType.SOCKET)
				.session(session)
				.decoder(decoder)
				.serverContext(serverContext)
				.codecThreadPool(codecThreadPool)
				.statsThreadPool(statsThreadPool)
				.requestQueues(requestQueues)
				.build();
		
		group.fireBytesReceived("hello".getBytes());
		EzySimplePacket packet = new EzySimplePacket();
		packet.setBinary(true);
		packet.setData("world".getBytes());
		packet.setTransportType(EzyTransportType.TCP);
		ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
		group.firePacketSend(packet, writeBuffer);
		group.sendPacketNow(packet);
		group.fireChannelRead(EzyCommand.PING, EzyEntityFactory.EMPTY_ARRAY);
		group.fireStreamBytesReceived(new byte[] {0, 1, 2});
		EzyPacket droppedPacket = mock(EzyPacket.class);
		when(droppedPacket.getSize()).thenReturn(12);
		group.addDroppedPacket(droppedPacket);
		EzyPacket failedPacket = mock(EzyPacket.class);
		when(failedPacket.getData()).thenReturn(new byte[] {1, 2, 3});
		when(failedPacket.isBinary()).thenReturn(true);
		when(channel.write(any(ByteBuffer.class), anyBoolean())).thenThrow(new IllegalStateException("maintain"));
		group.firePacketSend(failedPacket, writeBuffer);
		group.fireChannelInactive();
		Thread.sleep(2000);
		group.destroy();
		group.destroy();
		
		EzySocketStreamQueue streamQueue = new EzyBlockingSocketStreamQueue();
		group = (EzySimpleNioHandlerGroup) handlerGroupBuilderFactory.newBuilder(EzyConnectionType.SOCKET)
				.session(session)
				.streamQueue(streamQueue)
				.decoder(new ExStreamEzyByteToObjectDecoder())
				.serverContext(serverContext)
				.codecThreadPool(codecThreadPool)
				.statsThreadPool(statsThreadPool)
				.requestQueues(requestQueues)
				.build();
		((EzyAbstractSession)session).setStreamingEnable(false);
		group.fireBytesReceived("hello".getBytes());
		Thread.sleep(500);
		((EzyAbstractSession)session).setStreamingEnable(true);
		group.fireBytesReceived("hello".getBytes());
		Thread.sleep(1000);
		
		streamQueue = mock(EzySocketStreamQueue.class);
		when(streamQueue.add(any())).thenThrow(new IllegalStateException("maintain"));
		group = (EzySimpleNioHandlerGroup) handlerGroupBuilderFactory.newBuilder(EzyConnectionType.SOCKET)
				.session(session)
				.streamQueue(streamQueue)
				.decoder(new ExStreamEzyByteToObjectDecoder())
				.serverContext(serverContext)
				.codecThreadPool(codecThreadPool)
				.statsThreadPool(statsThreadPool)
				.requestQueues(requestQueues)
				.build();
		group.fireBytesReceived("hello".getBytes());
		Thread.sleep(1000);
		
		group = (EzySimpleNioHandlerGroup) handlerGroupBuilderFactory.newBuilder(EzyConnectionType.SOCKET)
				.session(session)
				.decoder(new ErrorEzyByteToObjectDecoder())
				.serverContext(serverContext)
				.codecThreadPool(codecThreadPool)
				.statsThreadPool(statsThreadPool)
				.requestQueues(requestQueues)
				.build();
		group.fireBytesReceived("hello".getBytes());
		Thread.sleep(300);
	}
	
	@SuppressWarnings("rawtypes")
	@Test
	public void writeSuccessTest() throws Exception {
		EzySessionTicketsQueue socketSessionTicketsQueue = new EzyBlockingSessionTicketsQueue();
		EzySessionTicketsQueue webSocketSessionTicketsQueue = new EzyBlockingSessionTicketsQueue();
		EzyStatistics statistics = new EzySimpleStatistics();
		EzyHandlerGroupBuilderFactory handlerGroupBuilderFactory = EzyHandlerGroupBuilderFactoryImpl.builder()
				.statistics(statistics)
				.socketSessionTicketsQueue(socketSessionTicketsQueue)
				.webSocketSessionTicketsQueue(webSocketSessionTicketsQueue)
				.build();
		EzySimpleSettings settings = new EzySimpleSettings();
		EzySimpleStreamingSetting streaming = settings.getStreaming();
		streaming.setEnable(true);
		EzySimpleServer server = new EzySimpleServer();
		server.setSettings(settings);
		EzySimpleServerContext serverContext = new EzySimpleServerContext();
		serverContext.setServer(server);
		serverContext.init();
		
		EzySessionManager sessionManager = EzyNioSessionManagerImpl.builder()
				.tokenGenerator(new EzySimpleSessionTokenGenerator())
				.build();
		EzyChannel channel = mock(EzyChannel.class);
		when(channel.isConnected()).thenReturn(true);
		when(channel.getConnection()).thenReturn(SocketChannel.open());
		when(channel.getConnectionType()).thenReturn(EzyConnectionType.SOCKET);
		EzyNioSession session = (EzyNioSession) sessionManager.provideSession(channel);
		ExEzyByteToObjectDecoder decoder = new ExEzyByteToObjectDecoder();
		ExecutorService codecThreadPool = EzyExecutors.newFixedThreadPool(1, "codec");
		ExecutorService statsThreadPool = EzyExecutors.newFixedThreadPool(1, "stats");
		EzySocketRequestQueues requestQueues = new EzySimpleSocketRequestQueues();
		
		when(channel.write(any(ByteBuffer.class), anyBoolean())).thenReturn(123456);
		
		EzySimpleNioHandlerGroup group = (EzySimpleNioHandlerGroup) handlerGroupBuilderFactory.newBuilder(EzyConnectionType.SOCKET)
				.session(session)
				.decoder(decoder)
				.serverContext(serverContext)
				.codecThreadPool(codecThreadPool)
				.statsThreadPool(statsThreadPool)
				.requestQueues(requestQueues)
				.build();
		EzySimplePacket packet = new EzySimplePacket();
		packet.setBinary(true);
		packet.setData("world".getBytes());
		packet.setTransportType(EzyTransportType.TCP);
		ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
		group.firePacketSend(packet, writeBuffer);
		Thread.sleep(1000);
	}
	
	@SuppressWarnings("rawtypes")
	@Test
	public void writeFailureTest() throws Exception {
		EzySessionTicketsQueue socketSessionTicketsQueue = new EzyBlockingSessionTicketsQueue();
		EzySessionTicketsQueue webSocketSessionTicketsQueue = new EzyBlockingSessionTicketsQueue();
		EzyStatistics statistics = new EzySimpleStatistics();
		EzyHandlerGroupBuilderFactory handlerGroupBuilderFactory = EzyHandlerGroupBuilderFactoryImpl.builder()
				.statistics(statistics)
				.socketSessionTicketsQueue(socketSessionTicketsQueue)
				.webSocketSessionTicketsQueue(webSocketSessionTicketsQueue)
				.build();
		EzySimpleSettings settings = new EzySimpleSettings();
		EzySimpleStreamingSetting streaming = settings.getStreaming();
		streaming.setEnable(true);
		EzySimpleServer server = new EzySimpleServer();
		server.setSettings(settings);
		EzySimpleServerContext serverContext = new EzySimpleServerContext();
		serverContext.setServer(server);
		serverContext.init();
		
		EzySessionManager sessionManager = EzyNioSessionManagerImpl.builder()
				.tokenGenerator(new EzySimpleSessionTokenGenerator())
				.build();
		EzyChannel channel = mock(EzyChannel.class);
		when(channel.isConnected()).thenReturn(true);
		when(channel.getConnection()).thenReturn(SocketChannel.open());
		when(channel.getConnectionType()).thenReturn(EzyConnectionType.SOCKET);
		EzyNioSession session = (EzyNioSession) sessionManager.provideSession(channel);
		ExEzyByteToObjectDecoder decoder = new ExEzyByteToObjectDecoder();
		ExecutorService codecThreadPool = EzyExecutors.newFixedThreadPool(1, "codec");
		ExecutorService statsThreadPool = EzyExecutors.newFixedThreadPool(1, "stats");
		EzySocketRequestQueues requestQueues = new EzySimpleSocketRequestQueues();
		
		SelectionKey selectionKey = mock(SelectionKey.class);
		when(selectionKey.isValid()).thenReturn(true);
		session.setProperty(EzyNioSession.SELECTION_KEY, selectionKey);
		
		EzySimpleNioHandlerGroup group = (EzySimpleNioHandlerGroup) handlerGroupBuilderFactory.newBuilder(EzyConnectionType.SOCKET)
				.session(session)
				.decoder(decoder)
				.serverContext(serverContext)
				.codecThreadPool(codecThreadPool)
				.statsThreadPool(statsThreadPool)
				.requestQueues(requestQueues)
				.build();
		EzySimplePacket packet = new EzySimplePacket();
		packet.setBinary(true);
		packet.setData("world".getBytes());
		packet.setTransportType(EzyTransportType.TCP);
		ByteBuffer writeBuffer = ByteBuffer.allocate(1024);
		group.firePacketSend(packet, writeBuffer);
		Thread.sleep(1000);
	}
	
	public static class ExEzyByteToObjectDecoder implements EzyByteToObjectDecoder {

		@Override
		public void reset() {}

		@Override
		public Object decode(EzyMessage message) throws Exception {
			return EzyEntityFactory.newArrayBuilder()
					.append(EzyCommand.PING.getId())
					.build();
		}

		@Override
		public void decode(ByteBuffer bytes, Queue<EzyMessage> queue) throws Exception {
			EzyMessageHeader header = new EzySimpleMessageHeader(false, false, false, false, false, false);
			byte[] content = new byte[bytes.remaining()];
			bytes.get(content);
			EzyMessage message = new EzySimpleMessage(header, content, content.length);
			queue.add(message);
		}
		
	}
	
	public static class ExStreamEzyByteToObjectDecoder implements EzyByteToObjectDecoder {

		@Override
		public void reset() {}

		@Override
		public Object decode(EzyMessage message) throws Exception {
			return EzyEntityFactory.newArrayBuilder()
					.append(EzyCommand.PING.getId())
					.build();
		}

		@Override
		public void decode(ByteBuffer bytes, Queue<EzyMessage> queue) throws Exception {
			EzyMessageHeader header = new EzySimpleMessageHeader(true, true, true, true, true, false);
			byte[] content = new byte[bytes.remaining()];
			bytes.get(content);
			EzyMessage message = new EzySimpleMessage(header, content, content.length);
			queue.add(message);
		}
		
	}
	
	public static class ErrorEzyByteToObjectDecoder implements EzyByteToObjectDecoder {

		@Override
		public void reset() {}

		@Override
		public Object decode(EzyMessage message) throws Exception {
			throw new IllegalStateException("maintain");
		}

		@Override
		public void decode(ByteBuffer bytes, Queue<EzyMessage> queue) throws Exception {
			throw new IllegalStateException("maintain");
		}
		
	}
}
