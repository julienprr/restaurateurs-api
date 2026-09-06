package fr.lesrestaurateurs.api.security;

import fr.lesrestaurateurs.api.domain.User;
import fr.lesrestaurateurs.api.web.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && User.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        Object user = request.getAttribute(AuthFilter.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);

        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        if (user == null && annotation != null && annotation.required()) {
            throw ApiException.unauthorized("Tu dois être connecté pour faire ça.");
        }
        return user;
    }
}
