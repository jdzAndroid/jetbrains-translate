【插件使用步骤】
第一步： 将翻译excel文件存放到项目目录下面最好是在项目代码目录 按下shift C快捷键即可将excel转换成json文件，
注意excel文件的顶部标注好翻译对应的国家代码，第0列存放翻译对应的key值，如果key值是数字，转换后的key默认转为以ido_key_为
前缀；
第二步：打开转换有的JSON文件所在目录下面的任何一个文件，在按下快捷键 shift G即可快速的生成每种语言对应的翻译dart文件；
_每次语言发生变更的时候调用下TranslateManager.localeChanged方法即可，获取翻译调用TranslateManager.translateProxy.
即可；
到此就实现了通过excel文件，快速实现国家化

其它辅助功能列表：
快速搜索：
1，选中需要搜索翻译内容的中文或者对应的key，按下shift s 即可以图形化UI将搜索出来的结果展示给你
搜索并替换成dart代码：
1，打开需要替换翻译的文件，按下shift r ,如果选中了文本就只替换选中的文本，没有选中就全局替换，并且
会自动在字符串结尾新增翻译的中文注释，方便查看，如果没有找到会使那部分代码爆红

其它功能正在迭代中

注意：
1，清理了插件的安装目录，会导致快速搜索插件不可用，需要重新触发下excel转json；
2，如果翻译中存在格式化的字符串，可以使用{your params name}格式填充，老的翻译%s、
%d等，默认会以{params+数字}的方式；

说明：
//将EXCEL文件转换成JSON文件
shift C 对应的另外一种触发方式是 鼠标右键 选中 generate菜单->ExcelToJson
//生成Dart文件
shift G 对应的另外一种触发方式是 鼠标右键 选中 generate菜单->GenerateDartFromJson
//搜索翻译并以图形化的形式展示出来
shift S 对应的另外一种触发方式是 鼠标右键 选中 generate菜单->QuickTipAction
//搜索并替换翻译
shift R 对应的另外一种触发方式是 鼠标右键 选中 generate菜单->SearchAndReplace
//自动给翻译新增注释，默认新增中文注释
shift A 对应的另外一种触发方式是 鼠标右键 选中 generate菜单->AutoAddLanguageDesc
