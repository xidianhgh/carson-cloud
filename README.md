# carson-cloud

#### 介绍
基于spring cloud的工程，加入一些实用的功能：
1.zuul网关集成jwt

## zuul网关集成jwt
#### 一、JwtUtil
为了方便，先封装好JwtUtil，主要包含两个方法，创建token和解析（并验证）token  
这里引用了第三方的包jjwt，简单好用，maven依赖如下
``` xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.9.1</version>
</dependency>
```
jwtUtil封装如下
``` java
@Component
public class JwtUtil {

    /**
     * 签名用的密钥
     */
    private static final String SIGNING_KEY = "78sebr72umyz33i9876gc31urjgyfhgj";

    /**
     * 用户登录成功后生成Jwt
     * 使用Hs256算法
     *
     * @param exp jwt过期时间
     * @param claims 保存在Payload（有效载荷）中的内容
     * @return token字符串
     */
    public String createJWT(Date exp, Map<String, Object> claims) {
        //指定签名的时候使用的签名算法
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

        //生成JWT的时间
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);

        //创建一个JwtBuilder，设置jwt的body
        JwtBuilder builder = Jwts.builder()
                //保存在Payload（有效载荷）中的内容
                .setClaims(claims)
                //iat: jwt的签发时间
                .setIssuedAt(now)
                //设置过期时间
                .setExpiration(exp)
                //设置签名使用的签名算法和签名使用的秘钥
                .signWith(signatureAlgorithm, SIGNING_KEY);

        return builder.compact();
    }

    /**
     * 解析token，获取到Payload（有效载荷）中的内容，包括验证签名，判断是否过期
     *
     * @param token
     * @return
     */
    public Claims parseJWT(String token) {
        //得到DefaultJwtParser
        Claims claims = Jwts.parser()
                //设置签名的秘钥
                .setSigningKey(SIGNING_KEY)
                //设置需要解析的token
                .parseClaimsJws(token).getBody();
        return claims;
    }

}
```

#### 二、自定义拦截器说明

继承自ZuulFilter，并注册到spring容器即可实现自定义拦截器，实现身份认证、参数校验、参数传递等功能

``` java
@Component
public class CustomFilter extends ZuulFilter {

    /**
     * filterType：过滤器类型
     * <p>
     * pre：路由之前
     * routing：路由之时
     * post： 路由之后
     * error：发送错误调用
     *
     * @return
     */
    @Override
    public String filterType() {
        return FilterConstants.PRE_TYPE;
//        return FilterConstants.POST_TYPE;
    }

    /**
     * filterOrder：过滤的顺序 序号配置可参照 https://blog.csdn.net/u010963948/article/details/100146656
     *
     * @return
     */
    @Override
    public int filterOrder() {
        return 0;
    }

    /**
     * shouldFilter：判断是否要执行过滤
     *
     * @return true表示需要过滤，将对该请求执行run方法
     */
    @Override
    public boolean shouldFilter() {
        return true;
    }

    /**
     * run：具体过滤的业务逻辑，可做身份验证，校验参数等等
     *
     * @return
     */
    @Override
    public Object run() throws ZuulException {
        //获取请求上下文对象
        RequestContext ctx = RequestContext.getCurrentContext();
        //获取request对象
        HttpServletRequest request = ctx.getRequest();
        //获取response对象
        HttpServletResponse response = ctx.getResponse();
        //添加请求头，传递到业务服务
        ctx.addZuulRequestHeader("xxx", "xxx");
        //添加响应头，返回给前端
        ctx.addZuulResponseHeader("xxx", "xxx");
        return null;
    }
}
```

#### 三、LoginAddJwtPostFilter，拦截登录方法，登录成功时创建token，返回给前端

要点：
1. 拦截类型是FilterConstants.POST_TYPE，在路由方法响应之后拦截
1. 判断请求的uri是否是登录接口（与配置文件中设置的登录uri是否匹配），需要在配置文件配置登录接口地址
1. 判断登录方法返回成功，创建token，并添加到 response body或response header，返回给前端

``` java
@Component
@Slf4j
public class LoginAddJwtPostFilter extends ZuulFilter {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    DataFilterConfig dataFilterConfig;

    /**
     * pre：路由之前
     * routing：路由之时
     * post： 路由之后
     * error：发送错误调用
     *
     * @return
     */
    @Override
    public String filterType() {
        return FilterConstants.POST_TYPE;
    }

    /**
     * filterOrder：过滤的顺序
     *
     * @return
     */
    @Override
    public int filterOrder() {
        return FilterConstants.SEND_RESPONSE_FILTER_ORDER - 2;
    }

    /**
     * shouldFilter：这里可以写逻辑判断，是否要过滤
     *
     * @return
     */
    @Override
    public boolean shouldFilter() {
        //路径与配置的相匹配，则执行过滤
        RequestContext ctx = RequestContext.getCurrentContext();
        for (String pathPattern : dataFilterConfig.getUserLoginPath()) {
            if (PathUtil.isPathMatch(pathPattern, ctx.getRequest().getRequestURI())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行过滤器逻辑，登录成功时给响应内容增加token
     *
     * @return
     */
    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        try {
            InputStream stream = ctx.getResponseDataStream();
            String body = StreamUtils.copyToString(stream, StandardCharsets.UTF_8);
            Result<HashMap<String, Object>> result = objectMapper.readValue(body, new TypeReference<Result<HashMap<String, Object>>>() {
            });
            //result.getCode() == 0 表示登录成功
            if (result.getCode() == 0) {
                HashMap<String, Object> jwtClaims = new HashMap<String, Object>() {{
                    put("userId", result.getData().get("userId"));
                }};
                Date expDate = DateTime.now().plusDays(7).toDate(); //过期时间 7 天
                String token = jwtUtil.createJWT(expDate, jwtClaims);
                //body json增加token
                result.getData().put("token", token);
                //序列化body json,设置到响应body中
                body = objectMapper.writeValueAsString(result);
                ctx.setResponseBody(body);

                //响应头设置token
                ctx.addZuulResponseHeader("token", token);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}

```

#### 四、JwtAuthPreFilter，拦截业务接口，验证token

要点：
1. 拦截类型是FilterConstants.PRE_TYPE，在调用业务接口之前拦截
1. 判断请求的uri是否是需要身份验证的接口（与配置文件中设置的uri是否匹配），需要在配置文件配置业务接口地址
1. 判断token验证是否通过，通过则路由，不通过返回错误提示

``` java
@Component
@Slf4j
public class JwtAuthPreFilter extends ZuulFilter {
    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtUtil jwtUtil;

    @Autowired
    DataFilterConfig dataFilterConfig;

    /**
     * pre：路由之前
     * routing：路由之时
     * post： 路由之后
     * error：发送错误调用
     *
     * @return
     */
    @Override
    public String filterType() {
        return FilterConstants.PRE_TYPE;
    }

    /**
     * filterOrder：过滤的顺序 序号配置可参照 https://blog.csdn.net/u010963948/article/details/100146656
     *
     * @return
     */
    @Override
    public int filterOrder() {
        return 2;
    }

    /**
     * shouldFilter：逻辑是否要过滤
     *
     * @return
     */
    @Override
    public boolean shouldFilter() {
        //路径与配置的相匹配，则执行过滤
        RequestContext ctx = RequestContext.getCurrentContext();
        for (String pathPattern : dataFilterConfig.getAuthPath()) {
            if (PathUtil.isPathMatch(pathPattern, ctx.getRequest().getRequestURI())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行过滤器逻辑，验证token
     *
     * @return
     */
    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        String token = request.getHeader("token");
        Claims claims;
        try {
            //解析没有异常则表示token验证通过，如有必要可根据自身需求增加验证逻辑
            claims = jwtUtil.parseJWT(token);
            log.info("token : {} 验证通过", token);
            //对请求进行路由
            ctx.setSendZuulResponse(true);
            //请求头加入userId，传给业务服务
            ctx.addZuulRequestHeader("userId", claims.get("userId").toString());
        } catch (ExpiredJwtException expiredJwtEx) {
            log.error("token : {} 过期", token );
            //不对请求进行路由
            ctx.setSendZuulResponse(false);
            responseError(ctx, -402, "token expired");
        } catch (Exception ex) {
            log.error("token : {} 验证失败" , token );
            //不对请求进行路由
            ctx.setSendZuulResponse(false);
            responseError(ctx, -401, "invalid token");
        }
        return null;
    }

    /**
     * 将异常信息响应给前端
     */
    private void responseError(RequestContext ctx, int code, String message) {
        HttpServletResponse response = ctx.getResponse();
        Result errResult = new Result();
        errResult.setCode(code);
        errResult.setMessage(message);
        ctx.setResponseBody(toJsonString(errResult));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=utf-8");
    }

    private String toJsonString(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.error("json序列化失败", e);
            return null;
        }
    }
}
```

#### 五、配置文件和路径匹配
在配置文件application.yml中配置登录接口路径 和 业务接口（需要身份验证的接口）路径，可配置多个，可使用通配符（基于Ant path匹配）
``` yaml
data-filter:
  auth-path: #需要验证token的请求地址,可设置多个,会触发JwtAuthPreFilter
    - /business/data/**
    - /business/report/**
  user-login-path: #登录请求地址,可设置多个,会触发LoginAddJwtPostFilter
    - /business/login/**
```
PathUtil，封装路径匹配方法，用于判断请求的接口是否是需要拦截的接口
``` java
public class PathUtil {

    private static AntPathMatcher matcher = new AntPathMatcher();

    public static boolean isPathMatch(String pattern, String path) {
        return matcher.match(pattern, path);
    }
}
```