# Logica - AI Enhancement Mod

基于Aperi Oculos框架的AI增强模组

## 功能特性

### 1. 转向限制系统
- 怪物在空闲/巡逻状态下转向角度限制（默认90度）
- 可配置的转向速度降低
- 支持单独实体转向速度配置

### 2. 听觉触发提示
- 听到声音时播放警报音效
- 怪物身上出现粒子效果
- 快速转向声音来源

### 3. 调查行为
- 前往声音来源地点
- 到达后环顾10秒（可配置）
- 未发现目标后继续游荡

### 4. AI策略系统

#### Guard（守卫）
- 返回生成位置
- 游荡半径6格（可配置）
- 自动防止面壁卡住

#### Sentries（哨兵）
- 快速巡逻，几乎不停歇
- 优先前往未访问位置
- 发现玩家发出3声钟声警报
- 吸引周围怪物前来

#### Patrol（巡逻）
- 沿路径点反复巡逻
- 听到声音时前往调查
- 调查完成返回巡逻路线

### 5. 战斗追踪系统
- 战斗中因低亮度丢失视觉时进入追踪模式
- 积极追踪所有声音（脚步、破坏方块等）
- 通过碰撞检测重新发现目标

## 使用方法

### 创建策略区域

1. 手持指南针右键点击两个角设置区域
2. 使用 `/logica strategy <type>` 填充策略标记方块
   - `guard` - 守卫策略
   - `sentries` - 哨兵策略
   - `patrol` - 巡逻策略

### 设置巡逻路线

1. 在创造模式下放置策略标记方块
2. 在策略标记方块相接的位置放置路径点方块
   - Patrol策略使用 `patrol_waypoint`
   - Sentries策略使用 `sentries_waypoint`
3. 路径点会自动被BFS算法搜索并连接
4. 支持立体路线（无视高度差）

### 标记方块特性

- **生存模式**：完全不可见，不可破坏
- **创造模式**：可见轮廓，可破坏
- **结构方块**：完全兼容，可保存和粘贴
- **贴图样式**：使用结构空位样式

## 配置选项

配置文件位于：`config/logica-common.toml`

### 主要配置项

```toml
[rotationControl]
maxIdleTurnAngle = 90.0              # 空闲时最大转向角度
globalRotationSpeedMultiplier = 0.3  # 全局转向速度倍率

[strategies]
guardRadius = 6.0                    # 守卫游荡半径
sentriesRadius = 32.0                # 哨兵巡逻半径
sentriesSpeedMultiplier = 1.3        # 哨兵移动速度倍率

[tracking]
maxTrackingDurationTicks = 600       # 追踪模式最大持续时间
collisionDetectionRadius = 1.5       # 碰撞检测半径
```

## 依赖

- Minecraft 1.20.1
- Forge 47.2.0+
- Aperi Oculos 1.0+

## 技术架构

- **事件驱动**：监听Aperi Oculos的感知事件
- **Capability系统**：存储实体AI状态和策略
- **标记方块**：使用隐形方块实现策略持久化
- **BFS路径搜索**：自动查找相连的路径点

## 开发状态

当前版本：1.0.0-alpha
状态：开发中

### 已实现
- ✅ 标记方块系统
- ✅ AI状态管理
- ✅ Capability系统
- ✅ 路径点搜索
- ✅ 配置系统

### 待实现
- ⏳ Goals行为实现
- ⏳ 事件监听器
- ⏳ Mixin转向控制
- ⏳ 音效和粒子效果

## 许可证

MIT License

## 作者

Sorcery Dynasties

## 致谢

- Aperi Oculos - 提供感知层框架
- Minecraft Forge - 模组加载器
