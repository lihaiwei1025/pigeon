package com.dianping.pigeon.remoting.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Future;

import org.apache.commons.lang.StringUtils;
import com.dianping.pigeon.log.Logger;

import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.provider.config.ProviderConfig;
import com.dianping.pigeon.remoting.provider.config.ServerConfig;
import com.dianping.pigeon.remoting.provider.domain.ProviderContext;
import com.dianping.pigeon.remoting.provider.process.RequestProcessor;
import com.dianping.pigeon.remoting.provider.process.RequestProcessorFactory;
import com.dianping.pigeon.remoting.provider.publish.ServiceChangeListener;
import com.dianping.pigeon.remoting.provider.publish.ServiceChangeListenerContainer;
import com.dianping.pigeon.util.FileUtils;
import com.dianping.pigeon.util.NetUtils;

public abstract class AbstractServer implements Server {

	protected final Logger logger = LoggerLoader.getLogger(this.getClass());
	RequestProcessor requestProcessor = null;
	ServerConfig serverConfig = null;

	public abstract void doStart(ServerConfig serverConfig);

	public abstract void doStop();

	public abstract <T> void doAddService(ProviderConfig<T> providerConfig);

	public abstract <T> void doRemoveService(ProviderConfig<T> providerConfig);

	public void start(ServerConfig serverConfig) {
		if (logger.isInfoEnabled()) {
			logger.info("server config:" + serverConfig);
		}
		requestProcessor = RequestProcessorFactory.selectProcessor(serverConfig);
		doStart(serverConfig);
		if (requestProcessor != null) {
			requestProcessor.start();
		}
		this.serverConfig = serverConfig;
	}

	public void stop() {
		doStop();
		if (requestProcessor != null) {
			requestProcessor.stop();
		}
	}

	@Override
	public <T> void addService(ProviderConfig<T> providerConfig) {
		requestProcessor.addService(providerConfig);
		doAddService(providerConfig);
		List<ServiceChangeListener> listeners = ServiceChangeListenerContainer.getListeners();
		for (ServiceChangeListener listener : listeners) {
			listener.notifyServiceAdded(providerConfig);
		}
	}

	@Override
	public <T> void removeService(ProviderConfig<T> providerConfig) {
		requestProcessor.removeService(providerConfig);
		doRemoveService(providerConfig);
		List<ServiceChangeListener> listeners = ServiceChangeListenerContainer.getListeners();
		for (ServiceChangeListener listener : listeners) {
			listener.notifyServiceRemoved(providerConfig);
		}
	}

	public RequestProcessor getRequestProcessor() {
		return requestProcessor;
	}

	@Override
	public ServerConfig getServerConfig() {
		return serverConfig;
	}

	@Override
	public Future<InvocationResponse> processRequest(InvocationRequest request, ProviderContext providerContext) {
		return requestProcessor.processRequest(request, providerContext);
	}

	public int getAvailablePort(int port) {
		int lastPort = port;
		String filePath = LoggerLoader.LOG_ROOT + "/pigeon-port.conf";
		File file = new File(filePath);
		Properties properties = null;
		String key = null;
		try {
			key = this.getClass().getResource("/").getPath() + port;
			if (file.exists()) {
				try {
					properties = FileUtils.readFile(new FileInputStream(file));
					String strLastPort = properties.getProperty(key);
					if (StringUtils.isNotBlank(strLastPort)) {
						lastPort = Integer.parseInt(strLastPort);
					}
				} catch (Exception e) {
				}
			}
		} catch (RuntimeException e) {
		}
		lastPort = NetUtils.getAvailablePort(lastPort);
		if (properties == null) {
			properties = new Properties();
		}
		if (key != null) {
			properties.put(key, lastPort);
		}
		try {
			FileUtils.writeFile(file, properties);
		} catch (IOException e) {
		}
		return lastPort;
	}

}
