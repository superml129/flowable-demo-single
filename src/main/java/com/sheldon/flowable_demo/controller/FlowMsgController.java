package com.sheldon.flowable_demo.controller;

import com.sheldon.flowable_demo.entity.FlowMsg;
import com.sheldon.flowable_demo.mapper.FlowMsgMapper;
import com.sheldon.flowable_demo.util.SseMsgManager;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/flowMsg")
public class FlowMsgController {
    @Resource
    private FlowMsgMapper flowMsgMapper;
    @Resource
    private SseMsgManager sseMsgManager;

    // SSE长连接
    @GetMapping("/connect")
    @ResponseBody
    public SseEmitter connect(HttpSession session){
        String loginUser = (String) session.getAttribute("loginUser");
        if(loginUser == null) return null;
        return sseMsgManager.connect(loginUser);
    }

    // 获取未读数
    @GetMapping("/unReadCount")
    @ResponseBody
    public Map<String,Object> unReadCount(HttpSession session){
        String loginUser = (String) session.getAttribute("loginUser");
        long count = 0;
        if(loginUser != null){
            count = flowMsgMapper.countUnRead(loginUser);
        }
        Map<String,Object> map = new HashMap<>();
        map.put("count",count);
        return map;
    }

    // 获取消息列表
    @GetMapping("/list")
    @ResponseBody
    public List<FlowMsg> list(HttpSession session){
        String loginUser = (String) session.getAttribute("loginUser");
        if(loginUser == null) return null;
        return flowMsgMapper.listByUser(loginUser);
    }

    // 全部标记已读
    @GetMapping("/readAll")
    @ResponseBody
    public String readAll(HttpSession session){
        String loginUser = (String) session.getAttribute("loginUser");
        if(loginUser != null){
            flowMsgMapper.readAll(loginUser);
        }
        return "ok";
    }

    // 清空当前用户的消息记录
    @GetMapping("/clear")
    @ResponseBody
    public String clear(HttpSession session){
        String loginUser = (String) session.getAttribute("loginUser");
        if(loginUser != null){
            flowMsgMapper.clearAll(loginUser);
        }
        return "ok";
    }
}