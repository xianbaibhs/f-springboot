package cn.xiaobai.admin.filter;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.PathMatchingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import cn.xiaobai.admin.service.AdminPermissionService;
import cn.xiaobai.admin.utils.SpringContextUtils;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

//1.编写基于 URL的过滤器
//为了让你们更好地理解 Shiro 过滤器是如何工作的，我们先来尝试自定义一个过滤器实现我们想要的功能。
//PathMatchingFilter 是 Shiro提供的路径过滤器，我们可以通过继承它来编写过滤放行条件，即判断是否具有相应权限。
//判断的逻辑为： 首先，判断当前会话对应的用户是否登录，如果未登录直接 false
//第二步，判断访问的接口是否有对应的权限，如果没有视为不需要权限即可访问，直接 true 如果需要权限，
//查询出当前用户对应的所有权限，遍历并与需要访问的接口进行比对，如果存在相应权限则 true，否则 false
/**
 * 
 * @ClassName: URLPathMatchingFilter
 * @Description:
 * 
 * @version 1.0
 * @author xiaobaibhs
 * @date 2020-03-11 09:31:52
 */

public class URLPathMatchingFilter extends PathMatchingFilter {
    @Autowired
    AdminPermissionService adminPermissionService;

    @Override
    protected boolean onPreHandle(ServletRequest request, ServletResponse response, Object mappedValue)
        throws Exception {
        HttpServletRequest httpServletRequest = (HttpServletRequest)request;
        HttpServletResponse httpServletResponse = (HttpServletResponse)response;

        if (HttpMethod.OPTIONS.toString().equals((httpServletRequest).getMethod())) {
            httpServletResponse.setStatus(HttpStatus.NO_CONTENT.value());
            return true;
        }
//1.在 Shiro的配置文件中，我们不能把 URLPathMatchingFilter用 @Bean被 Spring 管理起来。
//2. 原因是 Shiro 存在 bug, 这个也是过滤器，ShiroFilterFactoryBean 也是过滤器，
//   当他们都出现的时候，默认的什么 anno,authc 过滤器就失效了。所以不能把他声明为 @Bean。
//3.因此，我们无法在 URLPathMatchingFilter 中使用 @Autowired 注入 AdminPermissionService 类，
//   所以需要借助一个工具类利用 Spring 应用上下文获取 AdminPermissionService 的实例。

        if (null == adminPermissionService) {
            adminPermissionService = SpringContextUtils.getContext().getBean(AdminPermissionService.class);
        }

        String requestAPI = getPathWithinApplication(request);

        Subject subject = SecurityUtils.getSubject();

        if (!subject.isAuthenticated()) {
            System.out.println("需要登录");
            return false;
        }

        // 判断访问接口是否需要过滤（数据库中是否有对应信息）
        boolean needFilter = adminPermissionService.needFilter(requestAPI);
        if (!needFilter) {
            System.out.println("接口：" + requestAPI + "无需权限");
            return true;
        } else {
            // 判断当前用户是否有相应权限
            boolean hasPermission = false;
            String username = subject.getPrincipal().toString();
            Set<String> permissionAPIs = adminPermissionService.listPermissionURLsByUser(username);
            for (String api : permissionAPIs) {
                // 匹配前缀
                if (requestAPI.startsWith(api)) {
                    hasPermission = true;
                    break;
                }
            }

            if (hasPermission) {
                System.out.println("访问权限：" + requestAPI + "验证成功");
                return true;
            } else {
                System.out.println("当前用户没有访问接口" + requestAPI + "的权限");
                return false;
            }
        }
    }
}
