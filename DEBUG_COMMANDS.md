# LobotoCraft 调试命令

所有服务端调试命令都要求权限等级 2，统一前缀：

```text
/lobotocraft debug
```

旧命令仍然保留兼容；新的调试流程优先使用本文命令。

## 总状态

```text
/lobotocraft debug status [player]
/lobotocraft debug performance [path]
```

`status` 显示坐标、维度、四维等级、精神、当天工作和正式工作会话。
服务端命令中的 `performance` 只负责提示性能日志位置；性能计数器属于客户端，使用文末的客户端命令读取。

## 工作会话

```text
/lobotocraft debug work list
/lobotocraft debug work status [player]
/lobotocraft debug work start <player> <target> <type>
/lobotocraft debug work cancel <player>
/lobotocraft debug work force-complete <player> <good|normal|bad>
/lobotocraft debug work stop-continuous <player>
```

`start` 走正式服务端校验，因此会检查距离、登记、精神、等级和 AI 占用规则。

## AI 员工

```text
/lobotocraft debug employee list
/lobotocraft debug employee status <employee>
/lobotocraft debug employee cancel <employee>
/lobotocraft debug employee persist <employee>
/lobotocraft debug employee spawn <employee>
/lobotocraft debug employee move <employee> <x y z>
/lobotocraft debug employee work <employee> <target> <type>
/lobotocraft debug employee suppress <employee> <target>
/lobotocraft debug employee follow <employee> <target>
/lobotocraft debug employee tool <employee> <target>
```

`cancel` 只取消尚在寻路的待命令；正式工作开始后会拒绝取消。

## 异想体与登记

```text
/lobotocraft debug abnormality list [radius]
/lobotocraft debug abnormality status <target>
/lobotocraft debug abnormality register-check <target>
/lobotocraft debug abnormality qliphoth <target> <value>
/lobotocraft debug abnormality escape <target>
/lobotocraft debug abnormality return <target>
/lobotocraft debug abnormality heal <target>
```

`qliphoth` 只改数值，不自动触发出逃；需要测试出逃时使用 `escape`。

## 收容室

```text
/lobotocraft debug room status [marker]
/lobotocraft debug room scan [marker]
/lobotocraft debug room bind <marker> <target>
/lobotocraft debug room clear <marker>
```

省略 `marker` 时使用执行玩家脚下坐标。控制台执行必须显式传坐标。

## 电梯

```text
/lobotocraft debug elevator status [pos]
/lobotocraft debug elevator route <pos> <up|down>
/lobotocraft debug elevator try <pos> <up|down>
```

`route` 只检查目的电梯；`try` 使用玩家实体执行完整距离、冷却和落点碰撞校验。

## 设施日程

```text
/lobotocraft debug facility status
/lobotocraft debug facility requirements [player]
/lobotocraft debug facility next-day [count]
/lobotocraft debug facility energy <value>
/lobotocraft debug facility gauge <value>
/lobotocraft debug facility trigger-stage
/lobotocraft debug facility sync
```

`trigger-stage` 走正式融毁/考验触发流程；没有有效收容目标、生成点受阻或内容未完成时，不会伪造成功。
`requirements` 会逐项显示当天无法结束的真实原因，例如能源不足或部门扩建未确认。

## 外出计数

```text
/lobotocraft debug counter status [player]
/lobotocraft debug counter set <player> <value>
/lobotocraft debug counter add <player> <amount>
/lobotocraft debug counter full <player>
```

所有写入都会按当前设施日的上限截断，并立即同步客户端 HUD。

## 考验、核心抑制与部门

```text
/lobotocraft debug ordeal status
/lobotocraft debug suppression status
/lobotocraft debug suppression sync
/lobotocraft debug department status
/lobotocraft debug department set <department> <0..5>
```

`ordeal status` 同时显示黎明、正午和晚期考验的持久状态及追踪实体数。
`department set` 使用当前部门进度数据；控制部不能设为 0，区块加载与访问状态在服务端下一次刷新时更新。

## E.G.O

```text
/lobotocraft debug ego status [player]
```

显示装备槽、投影标记、等级需求，以及 AI 仓库中的武器/护甲分配编号。

## 客户端性能监测

客户端命令不经过服务器，单人游戏和联机普通玩家都可使用：

```text
/lobotocraft-client performance status
/lobotocraft-client performance dump
/lobotocraft-client performance reset
/lobotocraft-client performance path
```

- `status`：显示当前累计慢帧、慢 Tick、最慢耗时、FPS、实体数、区块数和当前监测分段。
- `dump`：立刻把一份完整摘要排队写入日志，并清空本统计周期的慢帧/慢 Tick 峰值。
- `reset`：清空计数器和各渲染分段统计，但不删除已有日志。
- `path`：显示日志的绝对路径；默认文件为 `logs/lobotocraft-client-performance.log`。
