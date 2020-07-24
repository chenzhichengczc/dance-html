
package com.jckc.dancer.config;

import com.jckc.dancer.common.exception.JcException;
import com.jckc.dancer.common.utils.IpConfig;
import com.jckc.dancer.common.utils.ResponseUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;


/**
 * @author ：fenghuang
 * @date ：Created in 2019/8/16 16:42
 * @description：自定义过滤器
 * @modified By：
 * @version:
 */

@Component
public class AuthenFilter implements Filter {

    @Resource
    private JwtConfig jwtConfig;

    private static final Logger logger = LoggerFactory.getLogger(AuthenFilter.class);

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {


        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String token = request.getHeader("token");
        //请求头token为空返回
        if(StringUtils.isEmpty(token)){
            logger.info("无token");
            throw new JcException(403, "当前访问拒绝");
        }
        logger.info("request = " + request.getServletPath());
        if(request.getServletPath().equals("/wx/api/get/refreshToken")){
            System.out.println("hello");
        }else {
            //token验证
            String ip = IpConfig.getRemoteAddr(request);
            boolean result = jwtConfig.verifyToken(token);
            if(!result){
                throw new JcException(401, "当前会话已过期,请刷新token");
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);

    }
}

