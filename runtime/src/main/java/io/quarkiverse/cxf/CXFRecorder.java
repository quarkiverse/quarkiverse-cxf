package io.quarkiverse.cxf;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkiverse.cxf.transport.CxfHandler;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class CXFRecorder {
    private static final Logger LOGGER = Logger.getLogger(CXFRecorder.class);

    public Supplier<CXFClientInfo> cxfClientInfoSupplier(String sei, CxfConfig cxfConfig,
            String soapBinding, String wsNamespace, String wsName, List<String> classNames) {
        LOGGER.trace("recorder CXFClientInfoSupplier");
        return () -> {
            // TODO suboptimal process. migrate to hashmap and get instead of loop
            for (Map.Entry<String, CxfEndpointConfig> webServicesByPath : cxfConfig.endpoints.entrySet()) {
                CxfEndpointConfig cxfEndPointConfig = webServicesByPath.getValue();
                String relativePath = webServicesByPath.getKey();
                if (!cxfEndPointConfig.serviceInterface.isPresent()) {
                    continue;
                }
                String cfgSei = cxfEndPointConfig.serviceInterface.get();
                if (cfgSei.equals(sei)) {
                    String endpointAddress = cxfEndPointConfig.clientEndpointUrl.orElse("http://localhost:8080");
                    if (!relativePath.equals("/") && !relativePath.equals("")) {
                        endpointAddress = endpointAddress.endsWith("/")
                                ? endpointAddress.substring(0, endpointAddress.length() - 1)
                                : endpointAddress;
                        endpointAddress = relativePath.startsWith("/") ? endpointAddress + relativePath
                                : endpointAddress + "/" + relativePath;
                    }

                    CXFClientInfo cfg = new CXFClientInfo();
                    cfg.init(sei,
                            endpointAddress,
                            cxfEndPointConfig.wsdlPath.orElse(null),
                            soapBinding,
                            wsNamespace,
                            wsName,
                            cxfEndPointConfig.endpointNamespace.orElse(null),
                            cxfEndPointConfig.endpointName.orElse(null),
                            cxfEndPointConfig.username.orElse(null),
                            cxfEndPointConfig.password.orElse(null),
                            classNames);
                    if (cxfEndPointConfig.inInterceptors.isPresent()) {
                        cfg.getInInterceptors().addAll(cxfEndPointConfig.inInterceptors.get());
                    }
                    if (cxfEndPointConfig.outInterceptors.isPresent()) {
                        cfg.getOutInterceptors().addAll(cxfEndPointConfig.outInterceptors.get());
                    }
                    if (cxfEndPointConfig.outFaultInterceptors.isPresent()) {
                        cfg.getOutFaultInterceptors().addAll(cxfEndPointConfig.outFaultInterceptors.get());
                    }
                    if (cxfEndPointConfig.inFaultInterceptors.isPresent()) {
                        cfg.getInFaultInterceptors().addAll(cxfEndPointConfig.inFaultInterceptors.get());
                    }
                    if (cxfEndPointConfig.features.isPresent()) {
                        cfg.getFeatures().addAll(cxfEndPointConfig.features.get());
                    }
                    return cfg;
                }
            }
            LOGGER.warn("the service interface config is not found for : " + sei);
            return null;
        };
    }

    public void registerCXFServlet(RuntimeValue<CXFServletInfos> runtimeInfos, String path, String sei,
            CxfConfig cxfConfig, String soapBinding, List<String> wrapperClassNames, String wsImplementor) {
        CXFServletInfos infos = runtimeInfos.getValue();
        for (Map.Entry<String, CxfEndpointConfig> webServicesByPath : cxfConfig.endpoints.entrySet()) {
            CxfEndpointConfig cxfEndPointConfig = webServicesByPath.getValue();
            String relativePath = webServicesByPath.getKey();

            if (cxfEndPointConfig.implementor.isPresent()) {
                String implementor = cxfEndPointConfig.implementor.get();
                if (implementor != null && implementor.equals(wsImplementor)) {
                    CXFServletInfo cfg = new CXFServletInfo(path,
                            relativePath,
                            implementor,
                            sei,
                            cxfEndPointConfig.wsdlPath.orElse(null),
                            soapBinding,
                            wrapperClassNames,
                            cxfEndPointConfig.publishedEndpointUrl.orElse(null));
                    if (cxfEndPointConfig.inInterceptors.isPresent()) {
                        cfg.getInInterceptors().addAll(cxfEndPointConfig.inInterceptors.get());
                    }
                    if (cxfEndPointConfig.outInterceptors.isPresent()) {
                        cfg.getOutInterceptors().addAll(cxfEndPointConfig.outInterceptors.get());
                    }
                    if (cxfEndPointConfig.outFaultInterceptors.isPresent()) {
                        cfg.getOutFaultInterceptors().addAll(cxfEndPointConfig.outFaultInterceptors.get());
                    }
                    if (cxfEndPointConfig.inFaultInterceptors.isPresent()) {
                        cfg.getInFaultInterceptors().addAll(cxfEndPointConfig.inFaultInterceptors.get());
                    }
                    if (cxfEndPointConfig.features.isPresent()) {
                        cfg.getFeatures().addAll(cxfEndPointConfig.features.get());
                    }

                    LOGGER.trace("register CXF Servlet info");
                    infos.add(cfg);
                }
            }
        }
    }

    public RuntimeValue<CXFServletInfos> createInfos() {
        CXFServletInfos infos = new CXFServletInfos();
        return new RuntimeValue<>(infos);
    }

    public Handler<RoutingContext> initServer(RuntimeValue<CXFServletInfos> infos) {
        LOGGER.trace("init server");
        return new CxfHandler(infos.getValue());
    }

    public void setPath(RuntimeValue<CXFServletInfos> infos, String path) {
        infos.getValue().setPath(path);
    }
}
