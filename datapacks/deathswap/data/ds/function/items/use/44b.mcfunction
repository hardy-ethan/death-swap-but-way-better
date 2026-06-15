$execute as @p[scores={permPNo=$(s)}] run attribute @s minecraft:scale base set 16.0
$tag @p[scores={permPNo=$(s)}] add huge_scale
$scoreboard players set @p[scores={permPNo=$(s)}] huge_scale 6000
$effect give @p[scores={permPNo=$(s)}] minecraft:jump_boost 51 3 true
scoreboard players add TimeS Items 2

$execute as @p[scores={permPNo=$(s)}] at @s run playsound minecraft:entity.spider.hurt master @s ~ ~ ~ 9 0.75
$title @p[scores={permPNo=$(s)}] title " "
$title @p[scores={permPNo=$(s)}] subtitle {"text":">> You are very beeg! <<","color":"green"}

$tellraw @a [{"text":">> ","color":"green"},{"selector":"@s","bold":false}," --> ",{"text":"Made ","color":"green"},{"selector":"@p[scores={permPNo=$(s)}]","bold":false},{"text":" extremely huge for 50 seconds","color":"green"}]

execute as @s run function ds:items/after_use