execute if score Lang Core matches 1 run tellraw @s [{"text":">> ","color":"dark_purple"},{"text":"You can use ''/trigger tp_away'' again if the area you're in gets too laggy!","color":"light_purple"}]

execute if score Lang Core matches 2 run tellraw @s [{"text":">> ","color":"dark_purple"},{"text":"如果你所在的区域变得过于卡顿, 可以再次使用 ''/trigger tp_away''!","color":"light_purple"}]

title @s title " "
execute if score Lang Core matches 1 run title @s subtitle {"text":"Use /trigger tp_away if it gets laggy!","color":"light_purple"}
execute if score Lang Core matches 2 run title @s subtitle {"text":"如果出现卡顿, 请使用 '/trigger tp_away'!","color":"light_purple"}

tag @s remove cant_tp_away
scoreboard players set @s used_tp_cycle 0
scoreboard players set @s tp_away 0
scoreboard players enable @s tp_away