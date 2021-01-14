package io.quarkiverse.cxf;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.CDI;
import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.common.spi.GeneratedNamespaceClassLoader;
import org.apache.cxf.common.spi.NamespaceClassCreator;
import org.apache.cxf.endpoint.dynamic.ExceptionClassCreator;
import org.apache.cxf.endpoint.dynamic.ExceptionClassLoader;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxb.FactoryClassCreator;
import org.apache.cxf.jaxb.FactoryClassLoader;
import org.apache.cxf.jaxb.WrapperHelperClassLoader;
import org.apache.cxf.jaxb.WrapperHelperCreator;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.spi.WrapperClassCreator;
import org.apache.cxf.jaxws.spi.WrapperClassLoader;
import org.apache.cxf.message.Message;
import org.apache.cxf.wsdl.ExtensionClassCreator;
import org.apache.cxf.wsdl.ExtensionClassLoader;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CxfClientProducer {

    private static final Logger LOGGER = Logger.getLogger(CxfClientProducer.class);

    public CXFClientInfo getInfo() {
        return null;
    }

    public Object loadCxfClient() {

        CXFClientInfo cxfClientInfo = getInfo();
        Class<?> seiClass;
        try {
            seiClass = Class.forName(cxfClientInfo.getSei(), false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            LOGGER.error("either webservice interface (client) or implementation (server) is mandatory");
            return null;
        }
        QuarkusClientFactoryBean quarkusClientFactoryBean = new QuarkusClientFactoryBean(cxfClientInfo.getClassNames());
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean(quarkusClientFactoryBean);
        Bus bus = quarkusClientFactoryBean.getBus(true);
        bus.setExtension(new WrapperHelperClassLoader(bus), WrapperHelperCreator.class);
        bus.setExtension(new ExtensionClassLoader(bus), ExtensionClassCreator.class);
        bus.setExtension(new ExceptionClassLoader(bus), ExceptionClassCreator.class);
        bus.setExtension(new WrapperClassLoader(bus), WrapperClassCreator.class);
        bus.setExtension(new FactoryClassLoader(bus), FactoryClassCreator.class);
        bus.setExtension(new GeneratedNamespaceClassLoader(bus), NamespaceClassCreator.class);
        factory.setServiceClass(seiClass);
        factory.setServiceName(new QName(cxfClientInfo.getWsNamespace(), cxfClientInfo.getWsName()));
        if (cxfClientInfo.getEpName() != null) {
            factory.setEndpointName(new QName(cxfClientInfo.getEpNamespace(), cxfClientInfo.getEpName()));
        }
        factory.setAddress(cxfClientInfo.getEndpointAddress());
        if (cxfClientInfo.getSoapBinding() != null) {
            factory.setBindingId(cxfClientInfo.getSoapBinding());
        }
        if (cxfClientInfo.getWsdlUrl() != null && !cxfClientInfo.getWsdlUrl().isEmpty()) {
            factory.setWsdlURL(cxfClientInfo.getWsdlUrl());
        }
        if (cxfClientInfo.getUsername() != null) {
            factory.setUsername(cxfClientInfo.getUsername());
        }
        if (cxfClientInfo.getPassword() != null) {
            factory.setPassword(cxfClientInfo.getPassword());
        }
        for (String feature : cxfClientInfo.getFeatures()) {
            addToCols(feature, factory.getFeatures(), Feature.class);
        }
        for (String inInterceptor : cxfClientInfo.getInInterceptors()) {
            addToCols(inInterceptor, factory.getInInterceptors());
        }
        for (String outInterceptor : cxfClientInfo.getOutInterceptors()) {
            addToCols(outInterceptor, factory.getOutInterceptors());
        }
        for (String outFaultInterceptor : cxfClientInfo.getOutFaultInterceptors()) {
            addToCols(outFaultInterceptor, factory.getOutFaultInterceptors());
        }
        for (String inFaultInterceptor : cxfClientInfo.getInFaultInterceptors()) {
            addToCols(inFaultInterceptor, factory.getInFaultInterceptors());
        }

        LOGGER.info("cxf client loaded for " + cxfClientInfo.getSei());
        return factory.create();
    }

    private void addToCols(String className, List<Interceptor<? extends Message>> cols) {
        /*
         * We use CastUtils to simplify an unchecked cast from
         * List<Interceptor<? extends Message>> to List<Interceptor>. For our
         * purposes this is ok since the parameterization of Interceptor is lost
         * at runtime anyway and we wouldn't be able enforce it without some
         * very complicated and very Interceptor-specific reflection code.
         */
        addToCols(className, CastUtils.<Interceptor> cast(cols), Interceptor.class);
    }

    private <T> void addToCols(String className, List<T> cols, Class<T> clazz) {
        Class<? extends T> cls;
        try {
            cls = Class.forName(className).asSubclass(clazz);
        } catch (ClassNotFoundException | ClassCastException e) {
            // silent failed
            return;
        }
        T item = null;
        try {
            item = CDI.current().select(cls).get();
            if (item != null) {
                cols.add(item);
            }
        } catch (ClassCastException | UnsatisfiedResolutionException e) {
            //silent fail
        }
        if (item != null) {
            return;
        }
        // if not found with beans just generate it.

        try {
            item = cls.getConstructor().newInstance();
            if (item != null) {
                cols.add(item);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
        }
    }
}
