execute as @s run function ds:items/use/all
tellraw @s {"text":"\n>> Click on which player you want to put in adventure mode for 60 seconds (they can't break/place blocks):","color":"aqua","italic":true}
scoreboard players set @s currItem 33
scoreboard players enable @s select
execute as @s run function ds:items/select_template