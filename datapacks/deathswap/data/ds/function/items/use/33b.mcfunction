$tag @p[scores={permPNo=$(s)}] add no_interaction
$scoreboard players set @p[scores={permPNo=$(s)}] no_interaction 3100
$execute as @p[scores={permPNo=$(s)}] run attribute @s minecraft:block_interaction_range base set 0.0
## $execute as @p[scores={permPNo=$(s)}] run attribute @s minecraft:entity_interaction_range base set 0.0
$title @p[scores={permPNo=$(s)}] title " "
$execute if score Lang Core matches 1 run title @p[scores={permPNo=$(s)}] subtitle {"text":">> You're in adventure mode for 60 secs! <<","color":"aqua"}
$execute if score Lang Core matches 2 run title @p[scores={permPNo=$(s)}] subtitle {"text":">> 你已进入冒险模式, 持续 60 秒! <<","color":"aqua"}

effect clear @a minecraft:night_vision

$tellraw @a [{"text":">> ","color":"green"},{"selector":"@s","bold":false}," --> ",{"text":"Put ","color":"aqua"},{"selector":"@p[scores={permPNo=$(s)}]","bold":false},{"text":" in adventure mode for 60 seconds","color":"aqua"}]

execute as @s run function ds:items/after_use