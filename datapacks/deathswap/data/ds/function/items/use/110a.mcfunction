execute as @s run function ds:items/use/all
tellraw @s {"text":"\n>> Click on which player you want to spawn 100 Endermen on:","color":"light_purple","italic":true}
scoreboard players set @s currItem 110
scoreboard players enable @s select
execute as @s run function ds:items/select_template