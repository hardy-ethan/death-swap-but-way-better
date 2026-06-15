team join Eliminated @s
tag @s remove playing
gamemode spectator @s
execute as @a at @s run playsound minecraft:entity.ender_dragon.growl master @s ~ ~ ~ 9 1.2
execute if score Lang Core matches 1 run title @s subtitle {"text":">> ELIMINATED! <<","color":"red"}
execute if score Lang Core matches 2 run title @s subtitle {"text":">> 被淘汰! <<","color":"red"}

tag @s add lower_pno_temp
execute as @a[tag=playing] if score @s itemPNo > @p[tag=lower_pno_temp] itemPNo run scoreboard players remove @s itemPNo 1
tag @s remove lower_pno_temp

scoreboard players set @s PNo 0
scoreboard players set @s itemPNo 0
scoreboard players set @s permPNo 0
tag @s add Shield
execute as @s run trigger select set 0
execute as @s run trigger tp_away set 0

scoreboard players add eliminations Core 1