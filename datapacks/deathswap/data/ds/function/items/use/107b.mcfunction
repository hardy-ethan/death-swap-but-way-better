$execute as @p[scores={permPNo=$(s)}] at @s run summon tnt ~ ~1 ~ {fuse:180,explosion_power:40,Tags:["ent"]}
$execute as @p[scores={permPNo=$(s)}] at @s run summon tnt ~2 ~1 ~ {fuse:180,explosion_power:40,Tags:["ent"]}
$execute as @p[scores={permPNo=$(s)}] at @s run summon tnt ~-2 ~1 ~ {fuse:180,explosion_power:40,Tags:["ent"]}
$execute as @p[scores={permPNo=$(s)}] at @s run summon tnt ~ ~1 ~2 {fuse:180,explosion_power:40,Tags:["ent"]}
$execute as @p[scores={permPNo=$(s)}] at @s run summon tnt ~ ~1 ~-2 {fuse:180,explosion_power:40,Tags:["ent"]}

$title @p[scores={permPNo=$(s)}] title [{"text":">> ","color":"gold","bold":true},{"text":"NUKE!!!","color":"red"},{"text":" <<","color":"gold"}]

$execute if score Lang Core matches 1 run title @p[scores={permPNo=$(s)}] subtitle [{"text":"Explodes in ","color":"red","bold":true},{"text":"12","color":"green"},{"text":" secs -- RUN TF AWAY!!","color":"red"}]

$execute if score Lang Core matches 2 run title @p[scores={permPNo=$(s)}] subtitle [{"text":"12","color":"green"},{"text":"秒后爆炸 -- 赶紧逃命!!","color":"red"}]

$tag @p[scores={permPNo=$(s)}] add 107alarm
setblock 19 50 15 minecraft:redstone_block
schedule function ds:items/misc/end_107alarm 4s

effect clear @a minecraft:night_vision

$tellraw @a [{"text":">> ","color":"green"},{"selector":"@s","bold":false}," --> ",{"text":"Summoned the Oppenheimer nuclear bomb on top of ","color":"red"},{"selector":"@p[scores={permPNo=$(s)}]","bold":false},{"text":" ","color":"red"}]

execute as @s at @s run playsound entity.tnt.primed master @s ~ ~ ~ 9
$execute as @p[scores={permPNo=$(s)}] at @s run playsound entity.tnt.primed master @s ~ ~ ~ 9

execute as @s run function ds:items/after_use