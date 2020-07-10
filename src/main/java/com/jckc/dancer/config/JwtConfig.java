package com.jckc.dancer.config;



import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jckc.dancer.basic.WxAccount;
import com.jckc.dancer.common.utils.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author ：fenghuang
 * @date ：Created in 2019/8/16 11:58
 * @description：
 * @modified By：
 * @version:
 */
@Component
public class JwtConfig {

    private static final Logger logger = LoggerFactory.getLogger(JwtConfig.class);

    /**
     * JWT 自定义密钥
     */
    private static final String SECRET_KEY = "5371f5f42244437139b";
    /**
     * JWT 过期时间值 这里写死为和小程序时间一致 7200 秒，也就是两个小时
     */
    private static long expire_time = 7200;

    private static long refresh_token_expire_time = 60 * 60 * 24;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 根据微信用户登陆信息创建 token
     * 注 : 这里的token会被缓存到redis中,用作为二次验证
     * redis里面缓存的时间应该和jwt token的过期时间设置相同
     *
     * @param wxAccount 微信用户信息
     * @return 返回 jwt token
     */
    public Map<String, String> createTokenByWxAccount(WxAccount wxAccount) {

        // header Map
        Map<String, Object> headMap = new HashMap<>();
        headMap.put("alg", "HS256");
        headMap.put("typ", "JWT");
        String jwtId = UUID.randomUUID().toString();   //JWT 随机ID,做为验证的key
        //1 . 加密算法进行签名得到token
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        String token = JWT.create()
                .withHeader(headMap) // header
                .withClaim("iss", "Service") // payload
                .withClaim("wxOpenId", wxAccount.getOpenId())
                .withClaim("sessionKey", wxAccount.getSessionKey())
                .withClaim("accessToken", wxAccount.getAccessToken())
                .withClaim("jwt-id", jwtId)
                .withExpiresAt(new Date(System.currentTimeMillis() + expire_time*1000)) //JWT 配置过期时间的正确姿势
                .sign(algorithm); // signature

        logger.info("创建的token: " + token);
        //2 . Redis缓存JWT, 注 : 请和JWT过期时间一致
        redisUtil.set("JWT-SESSION-" + jwtId, token, expire_time);
        Map<String, String> map = new HashMap<>();
        map.put("access_token", token);
        jwtId = UUID.randomUUID().toString();
        String refreshToken = JWT.create()
                .withHeader(headMap) // header
                .withClaim("iss", "Service") // payload
                .withClaim("wxOpenId", wxAccount.getOpenId())
                .withClaim("sessionKey", wxAccount.getSessionKey())
                .withClaim("accessToken", wxAccount.getAccessToken())
                .withClaim("jwt-id", jwtId)
                .withExpiresAt(new Date(System.currentTimeMillis() + refresh_token_expire_time*1000)) //JWT 配置过期时间的正确姿势
                .sign(algorithm);
        //2 . Redis缓存JWT, 注 : 请和JWT过期时间一致
        redisUtil.set("JWT-SESSION-" + jwtId, refreshToken, refresh_token_expire_time);
        map.put("refresh_token", refreshToken);
        return map;
    }
    /**
     * 校验token是否正确
     * 1 . 根据token解密，解密出jwt-id , 先从redis中查找出redisToken，匹配是否相同
     * 2 . 然后再对redisToken进行解密，解密成功则 继续流程 和 进行token续期
     *
     * @param token 密钥
     * @return 返回是否校验通过
     */
    public boolean verifyToken(String token) {
        try {
            logger.info("验证token: " + token);
            //1 . 根据token解密，解密出jwt-id , 先从redis中查找出redisToken，匹配是否相同
            String redisToken =  (String) redisUtil.get("JWT-SESSION-" + getJwtIdByToken(token));
            if (!redisToken.equals(token)) {
                return false;
            }
            //2 . 得到算法相同的JWTVerifier
            Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
            //在token中附带了username信息
            //JWTVerifier verifier = JWT.require(algorithm)
            //.build();
            //验证 token
            //DecodedJWT verify = verifier.verify(token);
            //Map<String, Claim> claims = verify.getClaims();
            //String ip1 = claims.get("ip").asString();
            //String a = JWT.decode(token).getClaim("ip").asString();
            logger.info("false 3");
            JWTVerifier verifier = JWT.require(algorithm)
                    .withClaim("wxOpenId", getWxOpenIdByToken(token))
                    .withClaim("sessionKey", getSessionKeyByToken(token))
                    .withClaim("accessToken", getAccessTokenByToken(token))
                    .withClaim("jwt-id", getJwtIdByToken(token))
                    .acceptExpiresAt(System.currentTimeMillis() + expire_time*1000 ) //JWT 正确的配置续期姿势
                    .build();
            //3 . 验证token
            verifier.verify(redisToken);
            //            //4 . Redis缓存JWT续期
            redisUtil.set("JWT-SESSION-" + getJwtIdByToken(token), redisToken, expire_time);
            return true;
        } catch (Exception e) { //捕捉到任何异常都视为校验失败
            logger.info("false 4");
            logger.info("exception e = " + e.getMessage());
            return false;
        }
    }

    /**
     * 验证refreshToken
     */
    public boolean verifyRefreshToken(String refreshToken, String token) {
        try {
            System.out.println("refreshToken = " + getJwtIdByToken(refreshToken));
            //1 . 根据token解密，解密出jwt-id , 先从redis中查找出redisRefreshToken，匹配是否相同
            String redisRefreshToken =  (String) redisUtil.get("JWT-SESSION-" + getJwtIdByToken(refreshToken));
            if (!redisRefreshToken.equals(refreshToken)) {
                return false;
            }
            //2 . 得到算法相同的JWTVerifier
            Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);

            JWTVerifier verifier = JWT.require(algorithm)
                    .build();
            //验证 token
            DecodedJWT verifyRefreshToken = verifier.verify(refreshToken);
            Map<String, Claim> claims = verifyRefreshToken.getClaims();
            //String ip1 = claims.get("ip").asString();
           // String a = JWT.decode(token).getClaim("ip").asString();
            DecodedJWT verifyToken = verifier.verify(token);
            Map<String, Claim> claimsToken = verifyToken.getClaims();
            //String ip2 = claimsToken.get("ip").asString();
            return true;
        } catch (Exception e) { //捕捉到任何异常都视为校验失败
            return false;
        }
    }

    /**
     * 根据Token获取wxOpenId(注意坑点 : 就算token不正确，也有可能解密出wxOpenId,同下)
     */
    public String getWxOpenIdByToken(String token) throws JWTDecodeException {
        return JWT.decode(token).getClaim("wxOpenId").asString();
    }

    /**
     * 根据Token获取sessionKey
     */
    public String getSessionKeyByToken(String token) throws JWTDecodeException {
        return JWT.decode(token).getClaim("sessionKey").asString();
    }


    /**
     * 根据Token获取wxOpenId(注意坑点 : 就算token不正确，也有可能解密出wxOpenId,同下)
     */
    public String getAccessTokenByToken(String token) throws JWTDecodeException {
        return JWT.decode(token).getClaim("accessToken").asString();
    }

    /**
     * 根据Token 获取jwt-id
     */
    private String getJwtIdByToken(String token) throws JWTDecodeException {
        return JWT.decode(token).getClaim("jwt-id").asString();
    }

    public String createNewToken(String openId, String sessionKey) {
        String jwtId = UUID.randomUUID().toString();   //JWT 随机ID,做为验证的key
        //1 . 加密算法进行签名得到token
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        String token = JWT.create()
               /* .withClaim("wxOpenId", wxAccount.getOpenId())
                .withClaim("sessionKey", wxAccount.getSessionKey())
                .withClaim("accessToken", wxAccount.getAccessToken())*/
                .withClaim("wxOpenId", openId)
                .withClaim("sessionKey", sessionKey)
                .withClaim("jwt-id", jwtId)
                .withExpiresAt(new Date(System.currentTimeMillis() + expire_time*1000)) //JWT 配置过期时间的正确姿势
                .sign(algorithm);
        //2 . Redis缓存JWT, 注 : 请和JWT过期时间一致
        redisUtil.set("JWT-SESSION-" + jwtId, token, expire_time);
        return token;
    }

}

