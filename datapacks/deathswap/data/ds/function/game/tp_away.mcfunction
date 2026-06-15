execute if score Lang Core matches 1 run tellraw @s [{"text":">> ","color":"dark_purple"},{"text":"You teleported somewhere far away from lag!","color":"light_purple"}]

execute if score Lang Core matches 2 run tellraw @s [{"text":">> ","color":"dark_purple"},{"text":"你传送到了一个远离卡顿的地方!","color":"light_purple"}]

spreadplayers 0 0 10000 29999000 false @s
execute as @s at @s run playsound minecraft:entity.enderman.teleport master @s ~ ~ ~ 9
scoreboard players set @s used_tp_cycle 0
scoreboard players set @s tp_away 0
tag @s add cant_tp_away