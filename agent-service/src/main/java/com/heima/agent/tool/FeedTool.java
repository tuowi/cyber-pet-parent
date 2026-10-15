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
public class FeedTool {

    @Autowired
    private PetClient petClient;

    @Autowired
    private ShopClient shopClient;

    @Tool("给指定宠物喂食。需要传宠物ID。会自动从背包中查找可用的食物道具，需要从用户消息中推断食品名称。如果没有指定食物名称，会自动使用背包里的第一个食物。只有当用户明确表达喂食意图时才调用。")
    public String feed(@P("宠物的id") Long petId,
                       @P("食物的名称，如'小鱼干'、'高级猫粮'，用户没指定则为空") String productName) {
        // 1.校验宠物的所属性
        Long userId = UserContextHolder.getUserId();
        PetInfo petInfo = petClient.getBasicPetInfo(petId);
        if (petInfo == null) {
            return "这个宠物不存在";
        }
        if (!petInfo.getUserId().equals(userId)) {
            return "这不是你的宠物，不能喂食。";
        }

        // 2.查用户背包
        List<UserBackpack> backpacks = shopClient.getBackpackList(userId);
        if (backpacks.isEmpty()) {
            return "主人，你的背包空了，先去商城买点吃的吧~";
        }

        // 聚集背包中的商品id
        List<Long> ids = backpacks.stream()
                .map(UserBackpack::getProductId)
                .distinct()
                .collect(Collectors.toList());

        // 根据商品id列表搜索商品信息
        List<Product> list = shopClient.getProductListByIds(ids);

        // 全局定义喂食的商品
        Product product;

        // 3. 找目标道具：指定了名称就精确匹配(模糊搜索)，没指定就拿第一个食物
        // 3.1.如果用户指定商品，根据商品名称搜索商品  需要模糊搜索，搜索最匹配的一个，需要食品种类
        if (productName != null && !productName.isBlank()) {
            // 3.1.1.根据名词模糊搜索
            product = shopClient.getProductLikeProductName(productName, InteractionType.FEED);

            // 如果没有找到，给用户提供背包中的食品信息
            if (product == null) {
                // 验证用户背包没有食物
                String collect = list.stream().filter(item -> item.getEffectType().equals(InteractionType.FEED))
                        .map(Product::getName).collect(Collectors.joining("、"));

                if (collect.isBlank()){
                    return "背包里没有食物了，主人快去商城买一些吧~";
                }
                return "背包里没有「" + productName + "」。主人你可以用这些食物：" +
                        collect;
            }
        }
        // 3.2.如果没有指定名称的商品，拿背包中第一个食品喂给宠物
        else {
            // 3.2.2.根据商品id列表提取食品
            product = list.stream()
                    .filter(item -> item.getEffectType().equals(InteractionType.FEED))
                    .findFirst()
                    .orElse(null);

            if (product == null) {
                return "背包里没有食物了，主人快去商城买一些吧~";
            }
        }

        // 3.进行喂食
        // 防护性校验：如果 product 或 product.getId() 为 null（可能因 Feign 错误反序列化导致），提前返回
        if (product == null || product.getId() == null) {
            return "主人，没有找到可用的食物哦~ 去商城买一些吧！";
        }
        InteractRequest interactRequest = new InteractRequest(InteractionType.FEED, product.getId());
        Result<InteractResultVO> interact = petClient.interact(petId, interactRequest);
        if (interact.getCode() != 200) {
            return "喂食失败：" + interact.getMessage();
        }
        // 4.工具调用
        InteractResultVO vo = interact.getData();
        ToolCallContext.record("FeedTool",
                "喂食" + petInfo.getPetName() + ",消耗 1 个" + product.getName() + ",饥饿值恢复到" + vo.getAttributes().getHunger() + "，心情值" + vo.getAttributes().getMood());

        // 5.返回信息
        return String.format("喂食成功！消耗 1 个 %s， %s 现在的饥饿值恢复到 %d，心情值 %d。%s",
                product.getName(),
                petInfo.getPetName(),
                vo.getAttributes().getHunger(),
                vo.getAttributes().getMood(),
                vo.getCuteMessage());
    }
}
