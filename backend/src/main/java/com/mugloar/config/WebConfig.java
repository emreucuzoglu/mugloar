package com.mugloar.config;

import lombok.RequiredArgsConstructor;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
class WebConfig implements WebMvcConfigurer {

  private final MugloarProperties props;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (props.corsOrigins().isEmpty()) {
      return;
    }
    registry
        .addMapping("/api/**")
        .allowedOrigins(props.corsOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST");
  }

  /** Tomcat's own error page, for requests rejected before Spring, names the server and version. */
  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> hideTomcatDetails() {
    return factory ->
        factory.addContextCustomizers(context -> {
          ErrorReportValve valve = new ErrorReportValve();
          valve.setShowReport(false);
          valve.setShowServerInfo(false);
          context.getParent().getPipeline().addValve(valve);
        });
  }
}
