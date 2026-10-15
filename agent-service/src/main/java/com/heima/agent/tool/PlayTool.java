package com.heima.agent.tool;

import com.heima.agent.utils.ToolCallContext;
import com.heima.api.client.PetClient;
import com.heima.api.client.ShopClient;
import com.heima.api.domain.dto.Result;
import com.heima.api.domain.po.PetInfo;
import com.heima.api.domain.po.Product;
import com.heima.api.domain.po.UserBackpack;
import com.heima.api.domain.query.InteractRequest;
import com.heima.api.domain.vo.InteractResultVO;
import com.heima.api.enums.InteractionType;
import com.heima.common.util.UserContextHolder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PlayTool {

    @Autowired
    private PetClient petClient;

    @Autowired
    private ShopClient shopClient;

    @Tool("跟指定宠物玩耍。需要传宠物ID。会自动从背包中查找可用的玩耍道具，需要从用户消息中推断玩具名称。如果没有指定玩具名称，会自动使用背包里的第一个玩具。只有当用户明确表达和宠物玩耍意图时才调用。")
    public String play(@P("宠物的id") Long petId,
                       @P("玩具的名称，如'玩具球'、'小羽毛'，用户没指定则为空") String productName) {
        // 1.校验宠物的所属性
        Long userId = UserContextHolder.getUserId();
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (petInfo == null) {
            return "这个宠物不存在";
        }
        if (!petInfo.getUserId().equals(userId)) {
            return "这不是你的宠物，不能和他玩耍。";
        }
        // 2.查用户背包
        List<UserBackpack> backpacks = shopClient.getBackpackList(userId);
        if (backpacks.isEmpty()) {
            return "主人，你的背包空了，先去商城买点玩具的吧~";
        }

        // 聚集背包中的商品id
        List<Long> ids = backpacks.stream()
                .map(UserBackpack::getProductId)
                .distinct()
                .collect(Collectors.toList());

        // 根据商品id列表搜索商品信息
        List<Product> list = shopClient.getProductListByIds(ids);

        // 全局定义玩耍的商品
        Product product;

        // 3. 找目标道具：指定了名称就精确匹配(模糊搜索)，没指定就拿第一个玩具
        // 3.1.如果用户指定商品，根据商品名称搜索商品  需要模糊搜索，搜索最匹配的一个，需要玩具种类
        if (productName != null && !productName.isBlank()) {
            // 3.1.1.根据名词模糊搜索
            product = shopClient.getProductLikeProductName(productName, InteractionType.PET);

            // 如果没有找到，给用户提供背包中的玩具信息
            if (product == null) {
                // 验证用户背包是否有澡具
                String collect = list.stream().filter(item -> item.getEffectType().equals(InteractionType.PET))
                        .map(Product::getName).collect(Collectors.joining("、"));

                if (collect.isBlank()){
                    // 用户背包没有玩具
                    return "背包里没有玩具了，主人快去商城买一些吧~";
                }
                return "背包里没有「" + productName + "」。主人你可以用这些玩具：" +
                        collect;
            }
        }
        // 3.2.如果没有指定名称的商品，拿背包中第一个食品喂给宠物
        else {
            // 3.2.2.根据商品id列表提取食品
            product = list.stream()
                    .filter(item -> item.getEffectType().equals(InteractionType.PET))
                    .findFirst()
                    .orElse(null);

            if (product == null) {
                return "背包里没有玩具了，主人快去商城买一些吧~";
            }
        }

        // 3.进行玩耍
        // 防护性校验：如果 product 或 product.getId() 为 null（可能因 Feign 错误反序列化导致），提前返回
        if (product == null || product.getId() == null) {
            return "主人，没有找到可用的玩具哦~ 去商城买一些吧！";
        }
        InteractRequest interactRequest = new InteractRequest(InteractionType.PET, product.getId());
        Result<InteractResultVO> interact = petClient.interact(petId, interactRequest);
        if (interact.getCode() != 200) {
            return "玩耍失败：" + interact.getMessage();
        }

        // 4.工具调用
        InteractResultVO vo = interact.getData();
        ToolCallContext.record("PlayTool",
                String.format("和 %s 玩耍，消耗 1 个 %s ，心情值恢复到 %d",
                        petInfo.getPetName(),
                        product.getName(),
                        vo.getAttributes().getMood()));

        // 5.返回信息
        return String.format("玩耍成功！消耗 1 个 %s ，%s 现在的心情值恢复到 %d。%s",
                product.getName(),
                petInfo.getPetName(),
                vo.getAttributes().getMood(),
                vo.getCuteMessage());
    }


    @Tool("轻轻抚摸指定的宠物，不需要任何道具，可以增加宠物的心情值。需要传宠物ID。当用户表达'摸摸'、'抚摸'、'撸猫/狗'等意图时调用此方法。")
    public String pet(@P("宠物的id") Long petId){
        // 1.校验宠物的所属性
        Long userId = UserContextHolder.getUserId();
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (petInfo == null) {
            return "这个宠物不存在";
        }
        if (!petInfo.getUserId().equals(userId)) {
            return "这不是你的宠物，不能和他玩耍。";
        }
        // 2.抚摸宠物
        InteractRequest interactRequest = new InteractRequest(InteractionType.PET, null);
        Result<InteractResultVO> interact = petClient.interact(petId, interactRequest);

        if (interact.getCode() != 200) {
            return "抚摸失败：" + interact.getMessage();
        }

        // 3.工具调用
        InteractResultVO vo = interact.getData();
        ToolCallContext.record("PlayTool",
                String.format("抚摸 %s ，心情值恢复到 %d",
                        petInfo.getPetName(),
                        vo.getAttributes().getMood()));

        // 4.返回信息
        return String.format("抚摸成功！%s 现在的心情值恢复到 %d。%s",
                petInfo.getPetName(),
                vo.getAttributes().getMood(),
                vo.getCuteMessage());
    }
}
