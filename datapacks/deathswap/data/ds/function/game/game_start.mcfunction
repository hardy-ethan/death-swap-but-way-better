execute as @a at @s run attribute @s minecraft:movement_speed base reset
execute as @a at @s run attribute @s minecraft:jump_strength base reset
execute as @a at @s run fill ~-5 ~-5 ~-5 ~5 ~5 ~5 minecraft:air replace minecraft:powder_snow

title @a title {"text":">> D.S. But Way Better! <<","color":"gold"}
title @a subtitle {"text":"Created by Jerries!","color":"yellow"}
execute as @a at @s run playsound minecraft:event.raid.horn master @s ~ ~ ~ 99 1

scoreboard players set gameOn Core 1
scoreboard players add TimeS Items 46
scoreboard players set timeRunning Core 1
scoreboard players set clockRunning Core 1
scoreboard objectives setdisplay sidebar Lives
gamemode survival @a[tag=playing]
execute if score pvp Core matches 1 run gamerule pvp true
execute if score pvp Core matches 0 run gamerule pvp false
function ds:game/reassign_pno
function ds:items/assign_pno
##execute as @a[tag=playing] run function ds:extra/inventory_glass
execute as @a[tag=playing] at @s run spawnpoint @s ~ ~ ~

gamemode spectator @a[tag=!playing]
tp @a[tag=!playing] @r[tag=playing]

execute if score Lang Core matches 1 run tellraw @a [{"text":"Map created by Jerries","color":"yellow","bold":true},{"text":" (Map version 1.0.3)","color":"yellow","bold":false,"italic":true}]
execute if score Lang Core matches 2 run tellraw @a [{"text":"地图由 Jerries 制作","color":"yellow","bold":true},{"text":" (地图版本 1.0.3)","color":"yellow","bold":false,"italic":true}]

tellraw @a {"text":"Additional datapack work by TheWorfer27 and Melumi11","color":"green","italic":true}

scoreboard players add TimeS Core 1

execute if score basicTools Core matches 1 run give @a[tag=playing] minecraft:stone_sword
execute if score basicTools Core matches 1 run give @a[tag=playing] minecraft:stone_pickaxe
execute if score basicTools Core matches 1 run give @a[tag=playing] minecraft:stone_axe
execute if score basicTools Core matches 1 run give @a[tag=playing] minecraft:stone_shovel
execute if score basicTools Core matches 1 run give @a[tag=playing] minecraft:crafting_table