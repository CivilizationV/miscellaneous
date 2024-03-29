# 代码中的坏味道


可读性，可维护性，可扩展性


## 命名

functions, modules, variables, classes

命名要能够清晰地表达功能和用法，调用者不需要关注内部如何实现。

get方法内部不做set，代码不能有副作用


## 重复代码

把重复代码抽成方法

相似代码通过调整代码顺序，把相似的部分抽取出来


## 过长的方法

从长方法中抽取短方法
条件表达式和循环


## 过长的参数列表

减少不必要的参数
把多个相关的参数封装成参数对象


## 全局数据

封装，通过方法控制变量的修改


## 可变数据

通过方法修改变量值，不能直接赋值

变量的作用域尽可能小


## 发散式变化

同一个模块由于不同的原因做不同的改变，一般是逻辑耦合

减少逻辑耦合，合理划分边界
高内聚，低耦合


## 散弹式手术

一次修改，需要对很多不同的类做很多小的修改。

先内联，再拆分


## Feature Envy

高内聚，低耦合
方法和相关数据封装到一起
将总是一起变化的东西放在一块儿


## 数据泥团

多个数据总是一起被使用
把数据团封装成类


## 基本类型滥用

适当地封装对象，如钱、坐标、范围等
以子类取代类型码
以多态取代条件表达式


## 重复的switch

使用多态替换重复的switch


## 循环

使用管道替换循环，stream
优点是更直观


## 无用或者多余的元素

删除无用代码
采用内联的方式去掉多余的抽象和封装


## 猜测的通用性

不要过度设计
不要过度抽象
删除无用代码


## 临时属性

类的某个属性只在特定情况下才被设置

给临时属性创建一个类，把操作这个属性的代码封装进这个类里
或者引入特例，在属性不被设置时，创建一个替代类


## 过长的消息链

抽取方法，把方法放到消息链适当的位置


## 中间人

某个类的接口有一半的函数都委托给其他类，应该移除中间人，直接和真正负责的对象打交道。
如果这样“不干实事”的函数只有少数几个，可以运用内联函数把它们放进调用端。
如果这些中间人还有其他行为，可以运用以委托取代超类或者以委托取代子类把它变成真正的对象，这样你既可以扩展原对象的行为，又不必负担那么多的委托动作。


## 内幕交易

模块间的数据交互要公开透明
合理划分模块
继承会造成模块间的耦合。以代理取代子类或以代理取代超类


## 大类

一个类有太多属性

抽取类
存在继承关系，抽取超类或者子类
临时属性的方法
消除重复代码
根据调用方的使用场景拆分类


## 异曲同工的类

匹配方法签名
建立类层级关系


## 数据类

不应该有public的属性
不能被修改的属性，不提供setter方法
不可变的属性不需要封装，可以直接通过属性获取，不需要通过getter方法。


## 被拒绝的遗赠

用代理替代继承


## 注释

如果你需要注释来解释一块代码做了什么，试试提炼函数；
如果函数已经提炼出来，但还是需要注释来解释其行为，试试用改变函数声明为它改名；
如果你需要注释说明某些系统的需求规格，试试引入断言。

