package com.heima.agent.tool;

import com.heima.agent.utils.ToolCallContext;
import com.heima.api.client.PetClient;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.PetInfo;
import com.heima.api.domain.vo.PetAttributeVO;
import com.heima.api.domain.vo.PetDetailVO;
import com.heima.common.util.UserContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CheckMoodTool {

    @Autowired
    private PetClient petClient;

    @Tool("根据宠物id查询宠物当前的心情、饥饿度、清洁度等状态")
    public String checkMood(@P("宠物的ID") Long petId) {
        Long userId = UserContextHolder.getUserId();
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (!petInfo.getUserId().equals(userId)){
            return "该宠物不属于您";
        }
        Result<PetDetailVO> result = petClient.PetDetail(petId);
        if (result.getCode() != 200 || result.getData() == null) return "呜呜，没找到这只宠物...";
        PetDetailVO pet = result.getData();
        PetAttributeVO attributes = pet.getAttributes();

        // 工具调用
        ToolCallContext.record("CheckMoodTool", "查询了" + pet.getPetName() + "的实时状态");

        return String.format(
                "宠物 %s 的状态：饥饿值 %d，心情值 %d，清洁值 %d，当前状态是 %s",
                pet.getPetName(),
                attributes.getHunger(), attributes.getMood(), attributes.getCleanliness(),
                pet.getState()
        );
    }
}
