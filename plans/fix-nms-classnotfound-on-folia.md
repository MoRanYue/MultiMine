# MultiMine 插件 Folia 兼容性修复方案

## 问题描述

MultiMine v1.3.1 在 Folia 26.1.2（Minecraft 1.21.8）服务器上运行时报错：

```
java.lang.NoClassDefFoundError: org/bukkit/craftbukkit/v1_21_R2/CraftWorld
```

## 根因分析

插件 [`DamagedBlock.java`](../src/main/java/net/sideways_sky/multimine/DamagedBlock.java) 直接引用了 Minecraft 服务端内部类（NMS/CraftBukkit），这些类在 Folia 上不存在或已被重定位。

### 涉及的 NMS 引用（全部在 DamagedBlock.java 中）

| 行号 | 代码 | 问题 |
|------|------|------|
| 9 | `import org.bukkit.craftbukkit.CraftWorld;` | CraftBukkit 内部类，Folia 不可用 |
| 10 | `import org.bukkit.craftbukkit.block.CraftBlock;` | 同上 |
| 26 | `entityId = net.minecraft.world.entity.Entity.nextEntityId();` | Mojang 映射类，Folia 不可用 |
| 101 | `((CraftWorld)...).getHandle().destroyBlockProgress(entityId, ..., progress);` | 直接调用 NMS 方法 |

## 修复方案

将 NMS 调用替换为 **Paper API 标准方法** `Player#sendBlockDamage(Block, float)`。

该方法在 Paper 1.21+ API 中可用，Folia 同样支持。

### 修改文件

仅需修改 **1 个文件**：[`src/main/java/net/sideways_sky/multimine/DamagedBlock.java`](../src/main/java/net/sideways_sky/multimine/DamagedBlock.java)

### 具体变更

#### 1. 移除 NMS 导入（第 9-10 行）

删除：
```java
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
```

#### 2. 替换 entityId 生成方式（第 26 行）

原代码：
```java
entityId = net.minecraft.world.entity.Entity.nextEntityId();
```

改为：
```java
entityId = block.hashCode();
```
*注：entityId 仅用于调试日志，不需要全局唯一，使用 block 的 hashCode 即可。*

#### 3. 重写 sendPacket 方法（第 100-102 行）

原代码：
```java
private void sendPacket(int progress){
    ((CraftWorld) block.getWorld()).getHandle().destroyBlockProgress(entityId, ((CraftBlock) block).getPosition(), progress);
}
```

改为：
```java
private void sendPacket(int progress){
    // progress: -1 = 停止破坏动画, 0-10 = 破坏阶段
    float damage = progress < 0 ? -1.0F : progress / 10.0F;
    for (Player player : block.getWorld().getPlayers()) {
        player.sendBlockDamage(block, damage);
    }
}
```

### 方法对应关系

| NMS 原方法 | Paper API 替代方法 |
|-----------|-------------------|
| `ServerLevel.destroyBlockProgress(entityId, pos, -1)` | `Player#sendBlockDamage(block, -1.0F)` — 清除动画 |
| `ServerLevel.destroyBlockProgress(entityId, pos, 0-10)` | `Player#sendBlockDamage(block, 0.0-1.0F)` — 设置进度 |

### 不受影响的功能

- `Player#breakBlock(Block)` — 已在 Paper API 中，无需修改
- 事件监听逻辑（Events.java）— 无 NMS 引用
- Folia 调度器（ScheduledTask）— 已是 Folia API，无需修改
- 构建配置（build.gradle）— PaperDevBundle 配置正确
- 插件配置（plugin.yml）— `folia-supported: true` 已设置

## 验证步骤

1. 运行 `./gradlew build` 确认编译通过
2. 将生成的 jar 部署到 Folia 服务器测试
3. 验证方块破坏动画正常显示和消退

## 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/main/java/net/sideways_sky/multimine/DamagedBlock.java` | 修改 | 替换 3 处 NMS 引用为 Paper API |
