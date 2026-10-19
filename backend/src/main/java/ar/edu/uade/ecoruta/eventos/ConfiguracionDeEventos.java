package ar.edu.uade.ecoruta.eventos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cualquier pedido que cambia algo (confirmar un incidente, despachar un camión, una lectura
 * de un sensor, un caso de demo) avisa a los paneles cuando termina bien. Así no hace falta
 * acordarse de avisar en cada controlador. Lo que hace el emulador por su cuenta avisa aparte.
 */
@Configuration
@EnableScheduling
class ConfiguracionDeEventos implements WebMvcConfigurer {

    // Las pruebas que levantan solo la capa web no tienen el canal
    private final ObjectProvider<CanalDeEventos> canal;

    ConfiguracionDeEventos(ObjectProvider<CanalDeEventos> canal) {
        this.canal = canal;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest pedido, HttpServletResponse respuesta, Object controlador,
                    Exception ex) {
                boolean cambia = !HttpMethod.GET.matches(pedido.getMethod()) && !HttpMethod.OPTIONS.matches(pedido.getMethod());
                if (cambia && ex == null && respuesta.getStatus() < 400) {
                    canal.ifAvailable(CanalDeEventos::avisar);
                }
            }
        }).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/auth/**");
    }
}
